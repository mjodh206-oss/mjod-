package app.majwuda.tools

/** Single place that assembles the full tool set offered to the model. */
object Tools {

    fun registry(ctx: ToolContext): ToolRegistry = ToolRegistry(
        CommunicationTools.register(ctx) +
            ProductivityTools.register(ctx) +
            DeviceTools.register(ctx) +
            AppTools.register(ctx) +
            KnowledgeTools.register(ctx) +
            ScreenTools.register(ctx),
    )
}
