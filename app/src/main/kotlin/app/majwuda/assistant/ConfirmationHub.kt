package app.majwuda.assistant

/** Bridge between the engine and the confirmation UI. */
object ConfirmationHub {

    @Volatile
    private var requester: ((toolUseId: String, toolName: String, prompt: String) -> Unit)? = null

    fun setRequester(handler: ((toolUseId: String, toolName: String, prompt: String) -> Unit)?) {
        requester = handler
    }

    fun requestConfirmation(toolUseId: String, toolName: String, prompt: String) {
        requester?.invoke(toolUseId, toolName, prompt)
    }
}
