require_dependency 'sandbox_helpers'

class Matchday < ActiveRecord::Base

  # named scopes
  named_scope :played, :conditions => "played_at IS NOT NULL"

  # validations
  validates_presence_of :contest
  validates_presence_of :when

  # associations
  has_many :matches, :dependent => :destroy, :as => :set
  has_many :slots, :class_name => "MatchdaySlot", :order => "position ASC"
  belongs_to :contest
  belongs_to :job, :dependent => :destroy, :class_name => "Delayed::Job"
  has_many :mini_jobs, :through => :matches, :source => :job

  # delegates OR :through associations
  has_many :match_slots, :through => :slots
  delegate :match_score_definition, :to => :contest

  # acts
  acts_as_list :scope => :contest_id

  def played?
    !played_at.nil?
  end

  def running?
    !job.nil? || !self.mini_jobs.empty?
  end

  def perform_delayed!
    matches.each do |match|
      match.perform_delayed!
    end
  end

  # Delayed::Job handler
  def perform
    matches.each do |match|
      match.perform
    end
  end

  def reset!(delete_games = false)
    raise "Can't reset while Job is running!" if running?
    
    Matchday.transaction do
      matches.each do |match|
        Match.benchmark("resetting match", Logger::DEBUG, false) do
          match.reset!(delete_games)
        end
      end

      self.played_at = nil
      save!
    end
  end

  # Callback (called by Match.perfom)
  def after_match_played(match)
    logger.info "Received after_match_played from #{match}"
    if all_matches_played?
      update_scoretable
      order_scoretable
      self.played_at = DateTime.now
      self.save!
    end
  end

  protected

  def all_matches_played?(force_reload = true)
    self.matches(force_reload).first(:conditions => { :played_at => nil }).nil?
  end

  def order_scoretable
    definition_fragments = match_score_definition.fragments.all
    joins = ["INNER JOIN scores AS order_scores ON order_scores.id = matchday_slots.score_id"]

    orders = []
    definition_fragments.each_with_index do |fragment, i|
      if fragment.orders?
        orders << "fragment_#{i}.value #{fragment.direction.upcase}"
        joins << ("INNER JOIN score_fragments AS fragment_#{i} ON (fragment_#{i}.score_id = order_scores.id AND fragment_#{i}.definition_id = #{fragment.id})")
      end
    end

    unless orders.empty?
      ranked_slots = slots(:reload).all(:order => orders.join(', '), :joins => joins.join(' '), :group => "matchday_slots.id")
      ranked_slots.each_with_index do |slot, i|
        writeable_slot = slots.find(slot.id)
        writeable_slot.position = i.next
        writeable_slot.save!
      end
    else
      logger.warn "ORDER was empty - cannot order."
    end
  end

  def update_scoretable
    slots.each do |slot|
      sandbox = Sandbox.new(contest.script_to_aggregate_matches)
      sandbox.extend SoftwareChallenge::ScriptHelpers::Aggregate

      # elements = [[1,0,0],[2,3,0],[3,0,0],[4,2,0]]
      elements = contest.matchdays(:reload).all(:conditions => ["played_at IS NOT NULL AND position < ?", position]).collect do |day|
        match_slot = day.match_slots(:reload).first(:conditions => ["matchday_slots.contestant_id = ?", slot.contestant.id])
        
        if match_slot and match_slot.score
          match_slot.score.to_a
        else
          nil
        end
      end
      
      elements << slot.match_slot.score.to_a

      nil_count = elements.size - elements.nitems
      logger.warn "array contained #{nil_count} nil elements" unless nil_count.zero?
      elements.compact!
      
      result = sandbox.invoke(:locals => {:elements => elements})

      if result.count != contest.match_score_definition.count
        raise "result (#{result.count}) did not match definition (#{contest.match_score_definition.count})"
      end

      score = slot.score
      unless score
        slot.score = slot.build_score(:definition => contest.match_score_definition)
      end
      slot.score.set!(result)
      slot.save!
    end
  end
end
