package sc.plugin2010;

import sc.plugin2010.Player.Action;

/**
 * @author rra
 * @since Jul 4, 2009
 * 
 */
public final class Move implements Cloneable
{
	/**
	 * Die aus Hase- und Igel bekannten Aktionen, sowie die Erweiterungen der
	 * CAU-Kiel
	 */
	public enum MoveTyp
	{
		/**
		 * Ziehe <code>n</code> Felder vor
		 */
		MOVE,
		/**
		 * Iß einen Salat - auf einem Salatfeld
		 */
		EAT,
		/**
		 * Nehme 10 Karotten auf oder gib 10 Karotten ab - auf einem
		 * Karottenfeld
		 */
		TAKE_OR_DROP_CARROTS,
		/**
		 * Falle zum letzten Igelfeld zurück.
		 */
		FALL_BACK,
		/**
		 * Spielt einen Hasenjoker aus - auf einem Hasenfeld
		 */
		PLAY_CARD,
		/**
		 * Im sehr seltenen Fall, wenn man sich gar nicht bewegen kann.
		 */
		SKIP
	}

	private int		n;
	private MoveTyp	typ;
	private Action	card;

	public Move(final MoveTyp t)
	{
		typ = t;
		card = null;
		n = 0;
	}

	public Move(final MoveTyp t, int val)
	{
		typ = t;
		card = null;
		n = val;
	}

	public Move(final MoveTyp t, final Action a)
	{
		typ = t;
		card = a;
		n = 0;
	}

	public Move(final MoveTyp t, final Action a, final int val)
	{
		typ = t;
		card = a;
		n = val;
	}

	public final int getN()
	{
		return n;
	}

	public final MoveTyp getTyp()
	{
		return typ;
	}

	public Action getCard()
	{
		return card;
	}

	@Override
	public String toString()
	{
		return "type=" + this.getTyp() + ", card=" + getCard() + ", n="
				+ getN();
	}

	@Override
	protected Move clone() throws CloneNotSupportedException
	{
		return (Move) super.clone();
	}
}
