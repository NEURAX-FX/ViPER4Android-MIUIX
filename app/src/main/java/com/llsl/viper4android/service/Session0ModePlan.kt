package com.llsl.viper4android.service

enum class Session0ModeStep {
    DEACTIVATE,
    DISPATCH_FULL_STATE,
    ACTIVATE,
}

fun session0ModePlan(
    globalMode: Boolean,
    useAidlTypeUuid: Boolean,
    alreadyActive: Boolean,
): List<Session0ModeStep> =
    when {
        !globalMode || useAidlTypeUuid -> listOf(Session0ModeStep.DEACTIVATE)
        alreadyActive -> listOf(Session0ModeStep.DISPATCH_FULL_STATE)
        else ->
            listOf(
                Session0ModeStep.DEACTIVATE,
                Session0ModeStep.DISPATCH_FULL_STATE,
                Session0ModeStep.ACTIVATE,
            )
    }
