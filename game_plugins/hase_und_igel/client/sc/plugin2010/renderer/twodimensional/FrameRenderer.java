/**
 * 
 */
package sc.plugin2010.renderer.twodimensional;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.ScrollPane;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.plugin2010.Board;
import sc.plugin2010.Client;
import sc.plugin2010.Move;
import sc.plugin2010.Player;
import sc.plugin2010.Player.FigureColor;
import sc.plugin2010.gui.GUIGameHandler;
import sc.plugin2010.renderer.Renderer;
import sc.plugin2010.util.GameUtil;

/**
 * @author ffi
 * 
 */
@SuppressWarnings("serial")
public class FrameRenderer extends JPanel implements Renderer, IClickObserver
{
	private static final Logger		logger			= LoggerFactory
															.getLogger(Client.class);

	// GUI Components
	private InformationBar			info;
	private ChatBar					chat;
	private ActionBar				actionb;
	private final List<FieldButton>	fbuttons		= new ArrayList<FieldButton>();
	private final GUIGameHandler	handler;

	// local instances of current players and board
	private Player					player;
	private Player					enemy;
	private Board					board;

	// only draw the board the first time it updates
	private boolean					boardWasCreated	= false;
	private boolean					myturn			= false;
	private boolean					onlyObserving	= false;

	// Strings used for asking Questions to the user
	private String					moveForward		= "Weiter ziehen";
	private String					takeCarrots		= "10 Karotten nehmen";
	private String					dropCarrots		= "10 Karotten abgeben";
	private String					carrotAnswer	= "carrots";

	private String					take20carrots	= "Nimm 20 Karotten";
	private String					doNothing		= "Nichts";
	private String					give20carrots	= "Gib 20 Karotten ab";
	private String					eatsalad		= "Friss sofort einen Salat";
	private String					hurryahead		= "Rücke eine Position vor";
	private String					fallback		= "Rücke eine Position vor";
	private String					jokerAnswer		= "joker";

	public FrameRenderer()
	{
		handler = null;
		createInitFrame();
	}

	public FrameRenderer(final GUIGameHandler handler,
			final boolean onlyObserving)
	{
		this.handler = handler;
		this.onlyObserving = onlyObserving;
		createInitFrame();
	}

	private void createInitFrame()
	{
		setDoubleBuffered(true);

		final BackgoundPane bg = new BackgoundPane("resource/background.png");

		for (int i = 0; i < 65; i++)
		{
			fbuttons.add(new FieldButton("", i, Board.FieldTyp.INVALID, this));
			fbuttons.get(i).setPreferredSize(new Dimension(40, 40));
			bg.add("1", fbuttons.get(i));
		}

		final HaseUndIgelLayout paneLayout = new HaseUndIgelLayout();

		bg.setLayout(paneLayout);

		info = new InformationBar();
		// chat = new ChatBar();
		actionb = new ActionBar();
		final ScrollPane action = new ScrollPane();
		action.add(actionb);

		final JPanel leftPanel = new JPanel();

		final BorderLayout layout = new BorderLayout();
		leftPanel.setLayout(layout);

		leftPanel.add(info, BorderLayout.NORTH);
		leftPanel.add(bg, BorderLayout.CENTER);
		// leftPanel.add(chat, BorderLayout.SOUTH);

		final BorderLayout framelayout = new BorderLayout();
		setLayout(framelayout);

		this.add(leftPanel, BorderLayout.CENTER);
		action.setPreferredSize(new Dimension(180, getHeight() - 100));
		this.add(action, BorderLayout.EAST);

		// chat.addOtherMessage("Chat: ");
		// chat.addOwnMessage("Prototyp: 0.1 alpha :)");

		setVisible(true);
	}

	private void displayAction(Move mov, String currentColor)
	{
		switch (mov.getTyp())
		{
			case EAT:
				actionb.addRow(currentColor + " frisst einen Salat");
				break;
			case MOVE:
				actionb.addRow(currentColor + " setzt auf "
						+ String.valueOf(mov.getN()));
				break;
			case TAKE_OR_DROP_CARROTS:
				if (mov.getN() == 10)
				{
					actionb.addRow(currentColor + " nimmt 10 Karotten");
				}
				else if (mov.getN() == -10)
				{
					actionb.addRow(currentColor + " gibt 10 Karotten ab");
				}
				break;
			case FALL_BACK:
				actionb.addRow(currentColor
						+ " lässt sich auf Igel zurückfallen");
				break;
			case PLAY_CARD:
				switch (mov.getCard())
				{
					case TAKE_OR_DROP_CARROTS:
						actionb.addRow(currentColor
								+ " spielt 'Nimm oder gib 20 Karotten'");
						break;
					case EAT_SALAD:
						actionb.addRow(currentColor
								+ " spielt 'Friss sofort einen Salat'");
						break;
					case FALL_BACK:
						actionb.addRow(currentColor
								+ " spielt 'Falle eine Position zurück'");
						break;
					case HURRY_AHEAD:
						actionb.addRow(currentColor
								+ " spielt 'Rücke eine Position vor'");
						break;
					default:
						break;
				}
				break;
			default:
				break;
		}
	}

	@Override
	public void updatePlayer(final Player player, final boolean own)
	{

		String currentColor = "";
		String currentOthersColor = "";
		String currentColorPath = "";
		switch (player.getColor())
		{
			case BLUE:
				currentColor = "Blau";
				currentColorPath = "blue";
				currentOthersColor = "Rot";
				break;
			case RED:
				currentColor = "Rot";
				currentColorPath = "red";
				currentOthersColor = "Blau";
				break;
			default:
				break;
		}

		info.setTurn(currentColorPath);
		actionb.removeAllRows();
		actionb.addRow("Aktionen: ");
		if (enemy != null) // TODO correct action adding
		{
			for (int i = 0; i < player.getHistory().size(); i++)
			{
				displayAction(player.getHistory().get(i), currentColor);
				for (int j = 0; j < enemy.getHistory().size(); j++)
				{
					if (i == j)
					{
						displayAction(enemy.getHistory().get(j),
								currentOthersColor);
					}
				}
			}
		}
		else
		{
			for (int i = 0; i < player.getHistory().size(); i++)
			{
				displayAction(player.getHistory().get(i), currentColor);
			}
		}

		for (int i = 0; i < fbuttons.size(); i++)
		{
			if (fbuttons.get(i).needRepaint(player.getColor()))
			{
				fbuttons.get(i).setFree();
				fbuttons.get(i).setReachable(false);
				fbuttons.get(i).repaint();
			}
		}

		if (enemy != null)
		{
			fbuttons.get(enemy.getPosition()).setOccupied(enemy.getColor());
		}

		fbuttons.get(player.getPosition()).setOccupied(player.getColor());

		if (own)
		{
			this.player = player;

			if (myturn)
			{
				setReachableFields(player.getPosition(), player
						.getCarrotsAvailable());

				if (player.getColor() == FigureColor.RED)
				{
					info.setColor(true);
				}
				else
				{
					info.setColor(false);
				}

				if (GameUtil.isValidToTakeOrDrop10Carrots(board, player, 10))
				{
					List<String> answers = new LinkedList<String>();
					answers.add(takeCarrots);
					if (GameUtil.isValidToTakeOrDrop10Carrots(board, player,
							-10))
					{
						answers.add(dropCarrots);
					}
					answers.add(moveForward);
					askQuestion("Was wollen Sie tun?", answers, carrotAnswer);
				}
				else if (GameUtil.isValidToEat(board, player))
				{
					sendMove(new Move(Move.MoveTyp.EAT));
				}
				else if ((board.getTypeAt(player.getPosition()) == Board.FieldTyp.RABBIT)
						&& (player.getActions().size() > 0))
				{
					List<String> answers = new LinkedList<String>();
					if (GameUtil.isValidToPlayCard(board, player,
							Player.Action.TAKE_OR_DROP_CARROTS, 20))
					{
						answers.add(take20carrots);
					}
					if (GameUtil.isValidToPlayCard(board, player,
							Player.Action.TAKE_OR_DROP_CARROTS, 0))
					{
						answers.add(doNothing);
					}
					if (GameUtil.isValidToPlayCard(board, player,
							Player.Action.TAKE_OR_DROP_CARROTS, -20))
					{
						answers.add(give20carrots);
					}
					if (GameUtil.isValidToPlayCard(board, player,
							Player.Action.EAT_SALAD, 0))
					{
						answers.add(eatsalad);
					}
					if (GameUtil.isValidToPlayCard(board, player,
							Player.Action.HURRY_AHEAD, 0))
					{
						answers.add(hurryahead);
					}
					if (GameUtil.isValidToPlayCard(board, player,
							Player.Action.FALL_BACK, 0))
					{
						answers.add(fallback);
					}
					askQuestion("Welchen Hasenjoker wollen Sie spielen?",
							answers, jokerAnswer);
				}
			}

			info.setAttributes(player.getCarrotsAvailable(), player
					.getSaladsToEat());
			info.setHasenjoker(player.getActions());
		}
		else
		{
			enemy = player;
			info.setEnemyAttributes(enemy.getCarrotsAvailable(), enemy
					.getSaladsToEat());
			info.setEnemyHasenjoker(enemy.getActions());
		}
	}

	@Override
	public void updateBoard(Board board, int round)
	{
		this.board = board;

		info.setRound(round + 1);

		if (!boardWasCreated)
		{
			String back = "";
			for (int i = 0; i < fbuttons.size(); i++)
			{
				switch (board.getTypeAt(i))
				{
					case CARROT:
						back = "resource/carrots.png";
						break;
					case HEDGEHOG:
						back = "resource/hedgehog.png";
						break;
					case RABBIT:
						back = "resource/rabbit.png";
						break;
					case SALAD:
						back = "resource/salad.png";
						break;
					case POSITION_1:
						back = "resource/position_1.png";
						break;
					case POSITION_2:
						back = "resource/position_2.png";
						break;
					case START:
						back = "resource/start.png";
						break;
					case GOAL:
						back = "resource/finish.png";
						break;
				}
				fbuttons.get(i).setBackground(back);
				fbuttons.get(i).setType(board.getTypeAt(i));
			}
			boardWasCreated = true;
		}
	}

	@Override
	public void updateAction(final String doneAction)
	{
		actionb.addRow(doneAction);
	}

	@Override
	public void updateChat(final String chatMsg)
	{
		chat.addOtherMessage(chatMsg);
	}

	public void askQuestion(final String question, final List<String> answers,
			String type)
	{
		new QuestionDialog(question, answers, this, type);
	}

	public void answerQuestion(final String answer, String type)
	{
		if (type.equals(carrotAnswer))
		{
			if (answer.equals(takeCarrots))
			{
				sendMove(new Move(Move.MoveTyp.TAKE_OR_DROP_CARROTS, 10));
			}
			else if (answer.equals(dropCarrots))
			{
				sendMove(new Move(Move.MoveTyp.TAKE_OR_DROP_CARROTS, -10));
			}
		}
		if (type.equals(jokerAnswer))
		{
			if (answer.equals(takeCarrots))
			{
				sendMove(new Move(Move.MoveTyp.PLAY_CARD,
						Player.Action.TAKE_OR_DROP_CARROTS, 20));
			}
			else if (answer.equals(doNothing))
			{
				sendMove(new Move(Move.MoveTyp.PLAY_CARD,
						Player.Action.TAKE_OR_DROP_CARROTS, 0));
			}
			else if (answer.equals(dropCarrots))
			{
				sendMove(new Move(Move.MoveTyp.PLAY_CARD,
						Player.Action.TAKE_OR_DROP_CARROTS, -20));
			}
			else if (answer.equals(eatsalad))
			{
				sendMove(new Move(Move.MoveTyp.PLAY_CARD,
						Player.Action.EAT_SALAD));
			}
			else if (answer.equals(hurryahead))
			{
				sendMove(new Move(Move.MoveTyp.PLAY_CARD,
						Player.Action.HURRY_AHEAD));
			}
			else if (answer.equals(fallback))
			{
				sendMove(new Move(Move.MoveTyp.PLAY_CARD,
						Player.Action.FALL_BACK));
			}
		}
	}

	private void setReachableFields(final int pos, final int carrots)
	{
		// if not in finish
		if (pos != 64)
		{
			final int moveable = GameUtil.calculateMoveableFields(carrots);

			int max = pos + moveable;

			if (max > 65)
			{
				max = 65;
			}

			for (int i = pos; i < max; i++)
			{
				if (fbuttons.get(i).getType() != Board.FieldTyp.HEDGEHOG)
				{
					fbuttons.get(i).setReachable(true);
					fbuttons.get(i).repaint();
				}
			}

			// if not on hedgehog
			if (fbuttons.get(pos).getType() != Board.FieldTyp.HEDGEHOG)
			{
				// seek for last hedgehog
				for (int i = pos - 1; i >= 0; i--)
				{
					if (fbuttons.get(i).getType() == Board.FieldTyp.HEDGEHOG)
					{
						fbuttons.get(i).setReachable(true);
						fbuttons.get(i).repaint();
						break;
					}
				}
			}
		}
	}

	/**
	 * user clicked on field <code>fieldNumber</code>
	 * 
	 * @param fieldNumber
	 *            the fieldnumber the user clicked onto
	 */
	public void updateClicked(final int fieldNumber)
	{
		if ((!onlyObserving) && (myturn))
		{
			int relativeFieldsToMove = fieldNumber - player.getPosition();
			if (relativeFieldsToMove < 0)
			{
				if (GameUtil.isValidToFallBack(board, player))
				{
					sendMove(new Move(Move.MoveTyp.FALL_BACK));
				}
				else
				{
					new ErrorDialog("Dies ist kein valider Zug.");
				}
			}
			else
			{
				if (GameUtil.isValidToMove(board, player, relativeFieldsToMove))
				{
					sendMove(new Move(Move.MoveTyp.MOVE, relativeFieldsToMove));
				}
				else
				{
					new ErrorDialog("Dies ist kein valider Zug.");
				}
			}
		}
	}

	@Override
	public Image getImage()
	{
		BufferedImage img = new BufferedImage(getWidth(), getHeight(),
				BufferedImage.TYPE_INT_RGB);
		paint(img.getGraphics());
		return img;
	}

	private void sendMove(Move move)
	{
		if (myturn)
		{
			handler.sendAction(move);
			myturn = false;
		}
	}

	@Override
	public void requestMove()
	{
		myturn = true;
		setReachableFields(player.getPosition(), player.getCarrotsAvailable());
	}
}
