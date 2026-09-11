package com.kunalan.emojikitchen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import com.kunalan.emojikitchen.ui.theme.LocalKitchenColors
import kotlinx.coroutines.launch

object KitchenRipple : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = RippleNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = javaClass.hashCode()
}

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val Grow = tween<Float>(200, easing = EaseOut)
private val FadeIn = tween<Float>(100, easing = EaseOut)
private val FadeOut = tween<Float>(200, easing = EaseOut)

private class RippleNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val scale = Animatable(0.4f)
    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> {
                        launch { scale.snapTo(0.4f); scale.animateTo(1f, Grow) }
                        launch { alpha.animateTo(0.1f, FadeIn) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch { alpha.animateTo(0f, FadeOut) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val a = alpha.value
        if (a > 0f) {
            drawCircle(currentValueOf(LocalKitchenColors).ripple, size.maxDimension * 0.71f * scale.value, alpha = a)
        }
        drawContent()
    }
}
