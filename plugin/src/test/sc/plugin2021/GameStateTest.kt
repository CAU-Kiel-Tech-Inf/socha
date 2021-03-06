package sc.plugin2021

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beInstanceOf
import sc.helpers.testXStream
import sc.plugin2021.util.Constants
import sc.plugin2021.util.GameRuleLogic
import sc.plugin2021.util.MoveMistake
import sc.shared.InvalidMoveException

class GameStateTest: WordSpec({
    "GameStates" When {
        val state = GameState(startPiece = PieceShape.PENTO_I)
        "constructed" should {
            "have an empty board" {
                state.board shouldBe Board()
            }
            "have each PieceShape available for each color" {
                Color.values().forEach { color ->
                    state.undeployedPieceShapes(color) shouldBe PieceShape.values().toSet()
                }
            }
            "start with no points for either player" {
                state.getPointsForPlayer(Team.ONE) shouldBe 0
                state.getPointsForPlayer(Team.TWO) shouldBe 0
            }
        }
        "turn number increases" should {
            "advance turn, round and currentcolor accordingly" {
                GameState().run {
                    orderedColors.size shouldBe Constants.COLORS
    
                    for (color in Color.values()) {
                        turn shouldBe color.ordinal
                        round shouldBe 1
                        currentColor shouldBe color
                        advance()
                    }
                    
                    turn shouldBe 4
                    round shouldBe 2
                    currentColor shouldBe Color.BLUE
    
                    advance(7)
                    turn shouldBe 11
                    round shouldBe 3
                    currentColor shouldBe Color.GREEN
    
                    removeActiveColor()
                    turn shouldBe 12
                    round shouldBe 4
                    currentColor shouldBe Color.BLUE
    
                    Color.values().filterNot { it == Color.RED }.forEach { color ->
                        (undeployedPieceShapes(color) as MutableCollection).clear()
                    }
                    
                    GameRuleLogic.removeInvalidColors(this)
                    turn shouldBe 14
                    currentColor shouldBe Color.RED
    
                    advance()
                    turn shouldBe 18
                    round shouldBe 5
                    currentColor shouldBe Color.RED
    
                    advance()
                    GameRuleLogic.removeInvalidColors(this)
                    turn shouldBe 22
                    round shouldBe 6
                    currentColor shouldBe Color.RED
                }
            }
        }
        "a piece is placed a second time" should {
            val move = SetMove(Piece(Color.BLUE, PieceShape.PENTO_I, Rotation.RIGHT, true))
            state.undeployedPieceShapes(Color.BLUE).size shouldBe 21
            shouldNotThrow<InvalidMoveException> {
                GameRuleLogic.performMove(state, move)
            }
            state.advance(3)
            state.currentColor shouldBe Color.BLUE
            state.undeployedPieceShapes(Color.BLUE).size shouldBe 20
            "throw an InvalidMoveException" {
                val ex = shouldThrow<InvalidMoveException> {
                    GameRuleLogic.performMove(state, move)
                }
                ex.mistake shouldBe MoveMistake.DUPLICATE_SHAPE
                state.undeployedPieceShapes(Color.BLUE).size shouldBe 20
            }
            "allow a SkipMove" {
                GameRuleLogic.performMove(state, SkipMove(Color.BLUE))
                state.lastMove should beInstanceOf<SkipMove>()
                testXStream.toXML(state) shouldContain Regex("lastMove.*SkipMove")
            }
        }
        "serialised and deserialised" should {
            val xStream = testXStream
            val transformed = xStream.fromXML(xStream.toXML(GameState(startPiece = state.startPiece))) as GameState
            "equal the original GameState" {
                transformed.toString() shouldBe state.toString()
                transformed shouldBe state

                GameRuleLogic.isFirstMove(transformed) shouldBe true
                transformed.getPointsForPlayer(Team.ONE) shouldBe 0
                transformed.board.isEmpty() shouldBe true
            }
        }
        "cloned" should {
            val cloned = state.clone()
            "preserve equality" {
                 cloned shouldBe state
            }
            "not equal original when lastMoveMono changed" {
                cloned shouldBe state
                cloned.lastMoveMono[Color.RED] = true
                cloned shouldNotBe state
            }
            "not equal original when undeployedPieces changed" {
                cloned shouldBe state
                GameRuleLogic.isFirstMove(cloned) shouldBe true
                cloned.removeUndeployedPiece(Piece(cloned.currentColor, cloned.undeployedPieceShapes().first()))
                GameRuleLogic.isFirstMove(cloned) shouldBe false
                cloned shouldNotBe state
            }
            "respect validColors" {
                state.removeActiveColor() shouldBe true
                state.currentColor shouldBe Color.YELLOW
                val newClone = state.clone()
                newClone shouldBe state
                cloned shouldNotBe state
                newClone.removeActiveColor() shouldBe true
                newClone.currentColor shouldBe Color.RED
                newClone shouldNotBe state
            }
            val otherState = GameState(lastMove = SetMove(Piece(Color.GREEN, 0)))
            "preserve inequality" {
                otherState shouldNotBe state
                otherState.clone() shouldNotBe state
            }
        }
    }
})