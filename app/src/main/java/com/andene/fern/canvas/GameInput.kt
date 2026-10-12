package com.andene.fern.canvas

import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/*
 * Input mapping (roadmap item 160): a game asks for actions ("left", "jump"), never for keys;
 * each action is bound to any number of inputs - keyboard keys, gamepad buttons and stick
 * directions, on-screen touch buttons, a finger anywhere on the screen, or the stylus - so one
 * game plays the same on a phone, with a keyboard or with a controller.
 */

/** The on-screen buttons a game can show while it plays. */
enum class TouchButton { LEFT, RIGHT, UP, DOWN, A, B }

/** One input an action listens to. */
sealed interface InputBinding {
    /** A keyboard key or gamepad button, by its Android key code. */
    data class Key(val code: Int) : InputBinding
    /** A gamepad stick axis pushed past half way in one direction ([positive] or not). */
    data class Axis(val axis: Int, val positive: Boolean) : InputBinding
    /** An on-screen button. */
    data class Touch(val button: TouchButton) : InputBinding
    /** A finger down anywhere on the game, outside the on-screen buttons. */
    data object Screen : InputBinding
    /** The stylus touching the game anywhere. */
    data object Stylus : InputBinding
}

/** A named action and the inputs that trigger it. */
data class InputAction(val name: String, val bindings: List<InputBinding>)

// MotionEvent's stick axes (AXIS_X, AXIS_Y), kept here so the mapping reads without a MotionEvent import.
const val AXIS_X = 0
const val AXIS_Y = 1

/** The actions a new game starts with: arrows/WASD, the d-pad and stick, on-screen buttons, space and enter. */
val DEFAULT_ACTIONS: List<InputAction> = listOf(
    InputAction("left", listOf(InputBinding.Key(KeyEvent.KEYCODE_DPAD_LEFT), InputBinding.Key(KeyEvent.KEYCODE_A), InputBinding.Axis(AXIS_X, false), InputBinding.Touch(TouchButton.LEFT))),
    InputAction("right", listOf(InputBinding.Key(KeyEvent.KEYCODE_DPAD_RIGHT), InputBinding.Key(KeyEvent.KEYCODE_D), InputBinding.Axis(AXIS_X, true), InputBinding.Touch(TouchButton.RIGHT))),
    InputAction("up", listOf(InputBinding.Key(KeyEvent.KEYCODE_DPAD_UP), InputBinding.Key(KeyEvent.KEYCODE_W), InputBinding.Axis(AXIS_Y, false), InputBinding.Touch(TouchButton.UP))),
    InputAction("down", listOf(InputBinding.Key(KeyEvent.KEYCODE_DPAD_DOWN), InputBinding.Key(KeyEvent.KEYCODE_S), InputBinding.Axis(AXIS_Y, true), InputBinding.Touch(TouchButton.DOWN))),
    InputAction("jump", listOf(InputBinding.Key(KeyEvent.KEYCODE_SPACE), InputBinding.Key(KeyEvent.KEYCODE_BUTTON_A), InputBinding.Touch(TouchButton.A))),
    InputAction("fire", listOf(InputBinding.Key(KeyEvent.KEYCODE_ENTER), InputBinding.Key(KeyEvent.KEYCODE_BUTTON_B), InputBinding.Touch(TouchButton.B))),
    InputAction("touch", listOf(InputBinding.Screen, InputBinding.Stylus)),
)

/**
 * What the player is doing while the game plays: the keys and buttons held, the sticks, the
 * on-screen buttons, and fingers and the stylus on the game (with where, in world space).
 * [isDown] says whether an action is held; [justPressed] whether it went down this step.
 */
class GameInputState(private val actions: List<InputAction>) {
    private val keys = HashSet<Int>()
    private val axes = HashMap<Int, Float>()
    private val buttons = HashSet<TouchButton>()
    private var screen = false
    private var stylus = false
    private var previous = emptySet<String>()
    private var current = emptySet<String>()

    /** Where the last finger or stylus on the game is, in world space; null when none is down. */
    var pointer: WorldPoint? = null
        private set

    fun key(code: Int, down: Boolean) { if (down) keys.add(code) else keys.remove(code) }
    fun axis(axis: Int, value: Float) { axes[axis] = value }
    fun touchButton(button: TouchButton, down: Boolean) { if (down) buttons.add(button) else buttons.remove(button) }

    /** A finger ([isStylus] false) or the stylus down at [at] (world), or lifted (null). */
    fun pointerAt(at: WorldPoint?, isStylus: Boolean) {
        if (isStylus) stylus = at != null else screen = at != null
        pointer = at
    }

    private fun active(binding: InputBinding): Boolean = when (binding) {
        is InputBinding.Key -> binding.code in keys
        is InputBinding.Axis -> (axes[binding.axis] ?: 0f).let { if (binding.positive) it > AXIS_THRESHOLD else it < -AXIS_THRESHOLD }
        is InputBinding.Touch -> binding.button in buttons
        InputBinding.Screen -> screen
        InputBinding.Stylus -> stylus
    }

    /** Takes in what's held now, at the start of a game step. */
    fun beginStep() {
        previous = current
        current = actions.filter { a -> a.bindings.any(::active) }.mapTo(HashSet()) { it.name }
    }

    fun isDown(action: String) = action in current
    fun justPressed(action: String) = action in current && action !in previous

    /** Every on-screen button some action uses, for the play screen to show. */
    val touchButtons: Set<TouchButton> = actions.flatMap { it.bindings }.filterIsInstance<InputBinding.Touch>().mapTo(LinkedHashSet()) { it.button }

    private companion object {
        const val AXIS_THRESHOLD = 0.5f
    }
}

/** Reading and writing actions in the game's JSON. */
object InputActionsJson {
    fun write(actions: List<InputAction>) = JSONArray().apply {
        for (a in actions) put(JSONObject().put("name", a.name).put("bindings", JSONArray().apply {
            for (b in a.bindings) put(
                when (b) {
                    is InputBinding.Key -> JSONObject().put("key", b.code)
                    is InputBinding.Axis -> JSONObject().put("axis", b.axis).put("positive", b.positive)
                    is InputBinding.Touch -> JSONObject().put("touch", b.button.name)
                    InputBinding.Screen -> JSONObject().put("screen", true)
                    InputBinding.Stylus -> JSONObject().put("stylus", true)
                },
            )
        }))
    }

    fun read(array: JSONArray): List<InputAction> = (0 until array.length()).mapNotNull { i ->
        val a = array.optJSONObject(i) ?: return@mapNotNull null
        val bindings = a.optJSONArray("bindings")?.let { list ->
            (0 until list.length()).mapNotNull { k ->
                val b = list.optJSONObject(k) ?: return@mapNotNull null
                when {
                    b.has("key") -> InputBinding.Key(b.getInt("key"))
                    b.has("axis") -> InputBinding.Axis(b.getInt("axis"), b.optBoolean("positive"))
                    b.has("touch") -> TouchButton.entries.firstOrNull { it.name == b.optString("touch") }?.let { InputBinding.Touch(it) }
                    b.optBoolean("screen") -> InputBinding.Screen
                    b.optBoolean("stylus") -> InputBinding.Stylus
                    else -> null
                }
            }
        }.orEmpty()
        a.optString("name").takeIf { it.isNotEmpty() }?.let { InputAction(it, bindings) }
    }
}
