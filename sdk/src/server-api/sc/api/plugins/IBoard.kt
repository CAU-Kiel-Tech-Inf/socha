package sc.api.plugins

interface IBoard: Cloneable {
    fun getField(x: Int, y: Int): IField
    
    public override fun clone(): IBoard
}