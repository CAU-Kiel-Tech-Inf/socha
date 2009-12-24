module SoChaManager
  require 'tempfile'
  require 'yaml'

  @@configuration = YAML.load_file(Rails.root.join('config', 'vm_watch.yml'))[Rails.env]
  @@watch_folder = (File.expand_path(@@configuration["watch_folder"], Rails.root) rescue Rails.root.join('tmp', 'vmwatch'))
  @@timeout = (@@configuration["timeout"] rescue 60).to_i
  @@start_game_after = (@@configuration["start_game_after"] rescue 30).to_i
  @@emulate_vm = (@@configuration["emulate_vm"] rescue false)

  mattr_reader :configuration, :watch_folder, :timeout, :start_game_after, :emulate_vm

  class Manager
    
    include Loggable

    HOST = "127.0.0.1"
    PORT = 13050
    HUI = 'swc_2010_hase_und_igel'

    if !File.exists? SoChaManager.watch_folder
      Dir.mkdir(SoChaManager.watch_folder)
    elsif !File.directory? SoChaManager.watch_folder
      raise "watch_folder (#{SoChaManager.watch_folder}) isn't a directory"
    end

    attr_reader :last_result

    def initialize
      logger.info "SoftwareChallenge Manager initialized."
    end
    
    def connect!(ip = HOST, port = PORT, game = HUI)
      @host, @port, @game = ip, port, game
      @client = Client.new ip, port
    end

    def play(round)
      manager = self
      player_names = round.slots.collect(&:ingame_name)

      @client.prepare HUI, player_names do |success,response|
        begin
          if success
            puts "Game has been prepared"

            reservations = response.xpath '//reservation'
            codes = reservations.collect(&:content)
            room_id = response.attributes['roomId'].value
            
            puts "Observing the game."

            logfile_name = "#{Time.now.to_i}_log_#{round.id}.xml.gz"
            logfile = Tempfile.new(logfile_name)
            gzip_logfile = Zlib::GzipWriter.open(logfile.path)

            room_handler = ObservingRoomHandler.new gzip_logfile do |observer|
              begin
                puts "Logging done!"
                gzip_logfile.close
                logfile.close

                # add original_filename attribute for Paperclip
                logfile_handle = logfile.open
                def logfile_handle.original_filename=(x); @original_filename = x; end
                def logfile_handle.original_filename; @original_filename; end
                logfile_handle.original_filename = logfile_name

                # save replay to database
                round.replay = logfile_handle
                round.save!

                @last_result = observer.result
                manager.close
              ensure
                logfile.close unless logfile.closed?
                logfile.unlink
              end
            end

            mutex = Mutex.new
            resource1 = ConditionVariable.new
            resource2 = ConditionVariable.new
            can_signal = false
            observation_success = false

            @client.observe room_id, "swordfish" do |success,response|
              puts "ObservationRequest: #{success}"
              
              if success
                @client.register_room_handler room_id, room_handler
                observation_success = true
              else
                manager.close
              end

              mutex.synchronize {
                resource2.lock(mutex) unless can_signal
                resource1.signal
              }
            end

            # wait for registration of observation-handler
            mutex.synchronize {
              can_signal = true
              resource2.signal
              resource1.wait(mutex)
            }

            if observation_success
              zip_files = []
              ActiveRecord::Base.benchmark "Preparing the Client VMs" do
                zip_files = round.slots.zip(codes).collect do |slot, code|
                  start_client(slot, code)
                end
              end

              puts "All clients have been prepared"
            
              emulate_vm_watcher! zip_files if SoChaManager.emulate_vm

              # force gamestart
              Thread.new do
                begin
                  puts "start_game_after #{SoChaManager.start_game_after} seconds"
                  sleep SoChaManager.start_game_after

                  threshold = [[SoChaManager.start_game_after, 20].min, 0].max

                  if room_handler.done?
                    puts "Game is already over, start_game_after not necessary."
                  elsif !room_handler.received_data_after?(threshold.seconds.ago)
                    puts "Invoking handler for start_game_after."
                    @client.step(room_id, true)
                  else
                    puts "Action detected, start_game_after not necessary."
                  end
                rescue => e
                  logger.log_formatted_exception e
                end
              end
            else
              puts "observation failed"
            end
          else
            puts "Couldn't prepare game!"
            @client.close
          end
        rescue => e
          logger.log_formatted_exception e
          raise
        end
      end
    end

    def done?
      @client.done?
    end
    
    def close
      @client.close
    end
    
    protected

    def emulate_vm_watcher!(zip_files)
      puts "Starting clients without VM"
      zip_files.each { |path| puts path }

      zip_files.each do |file|
        Thread.new do
          begin
            run_without_vm!(file)
          rescue => e
            logger.log_formatted_exception e
          end
        end
      end
    end

    def run_without_vm!(path)
      # make it absolute
      path = File.expand_path(path)

      # assert that we have the output directory
      output_directory = File.join(RAILS_ROOT, 'tmp', 'vmwatch_extract')
      Dir.mkdir output_directory unless File.directory? output_directory

      # create a directory to extrat the zip file
      directory = File.join(output_directory, File.basename(path))
      Dir.mkdir directory

      validate_zip_file(path)

      # extract
      puts "Extracting AI program..."
      full_output_path = File.expand_path(directory)
      command = %{unzip -oqq #{path} -d #{full_output_path}}
      puts command
      system command
      
      raise "failed to unzip" unless $?.exitstatus == 0

      puts "Starting AI program and waiting for termination..."
      command = %{sh -c "cd #{full_output_path}; ./startup.sh"}
      puts command
      system command
      
      puts "AI program has been executed and returned (exitcode: #{$?.exitstatus})"
    end

    def start_client(slot, reservation)
      puts "Starting client (id=#{slot.client.id}) for '#{slot.name}'"
      silent = true

      ai_program = slot.client
      file = ai_program.file.path

      # add "./" as a prefix
      executable = ai_program.main_file_entry.file_name

      if ai_program.java?
        executable = "java -Dfile.encoding=UTF-8 -jar #{executable}"
      else
        executable = File.join(".", executable)
      end

      target = nil
      
      Dir.mktmpdir(File.basename(file)) do |dir|
        command = %{unzip -qq #{File.expand_path(file)} -d #{dir}}
        puts command
        system command

        startup_file = File.join(dir, 'startup.sh')
        File.open(startup_file, 'w+') do |f|
          f.puts("#!/bin/bash")
          command = "#{executable} --host #{@host} --port #{@port} --reservation #{reservation}"
          command << " > /dev/null 2>&1" if silent
          f.puts command
          f.flush
        end
        
        File.chmod(0766, startup_file)
        
        key = slot.ingame_name
        generated_file_name = "#{Time.now.to_i}_#{key}_#{(rand * 1000).ceil}.zip"
        target = File.expand_path(File.join(SoChaManager.watch_folder, generated_file_name))
        
        command = %{sh -c "cd #{dir}; zip -qr #{target} ."}
        puts command
        system command
      end

      target
    end
    
    def validate_zip_file(path)
      # check zip for defects
      puts "Checking zip-file for defects..."
      command = %{unzip -qqt #{path}}
      puts command
      system command
      
      # repair if broken
      unless $?.exitstatus == 0
        puts "Zip-file is broken. Trying to fix..."
        
        fixed_path = "#{path}.fixed"
        command = %{zip -qFF #{path} --out #{fixed_path}}
        puts command
        system command
        
        raise "Couldn't fix broken zip-file" unless $?.exitstatus == 0
          
        puts "Sucessfully fixed zip-file"
        File.unlink(path)
        File.move(fixed_path, path)
      end
    end
  end
end