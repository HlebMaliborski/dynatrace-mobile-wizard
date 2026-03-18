package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.util.WizardColors
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

/**
 * Compact horizontal step-progress indicator drawn above the wizard tab pane.
 *
 * Layout (per step):
 *  ● — completed (filled green circle)
 *  ◉ — current   (filled accent circle, bold label)
 *  ○ — future    (grey outline, grey label)
 *
 * Circles are connected by a thin line that turns green as steps are completed.
 * Clicking a circle invokes [onStepClicked] so callers can drive tab navigation.
 *
 * All sizes are HiDPI-aware via [JBUI.scale].
 */
class WizardStepBar(val steps: List<String>) : JPanel() {

    /** Index of the currently active step (0-based). Triggers repaint on change. */
    var currentStep: Int = 0
        set(value) {
            field = value.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
            repaint()
        }

    /**
     * Invoked when the user clicks a step circle.
     * The caller is responsible for actually switching tabs (and running validation).
     */
    var onStepClicked: ((Int) -> Unit)? = null

    init {
        isOpaque = false
        border = JBUI.Borders.empty(8, 20, 6, 20)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val cb = onStepClicked ?: return
                val i = stepIndexAt(e.x)
                if (i in steps.indices) cb(i)
            }
            override fun mouseEntered(e: MouseEvent) = updateCursor(e.x)
            override fun mouseMoved(e: MouseEvent)   = updateCursor(e.x)
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent)   = updateCursor(e.x)
        })
    }

    override fun getPreferredSize(): Dimension = Dimension(0, JBUI.scale(54))
    override fun getMinimumSize():   Dimension = preferredSize

    // ── Painting ──────────────────────────────────────────────────────────────

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (steps.isEmpty()) return

        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val ins   = insets
            val drawW = width - ins.left - ins.right
            val n     = steps.size
            val stepW = drawW / n
            val offX  = ins.left

            val r       = JBUI.scale(9)
            val circleY = ins.top + r                      // circle centre Y
            val labelY  = circleY + r + JBUI.scale(13)    // label baseline

            val accentCol: Color = WizardColors.accent
            val doneCol:   Color = WizardColors.success
            val futureCol: Color = UIUtil.getContextHelpForeground()
            val bgCol:     Color = UIUtil.getPanelBackground()
            val labelCol:  Color = UIUtil.getLabelForeground()

            val smallFont = JBUI.Fonts.smallFont()
            val boldFont  = smallFont.deriveFont(Font.BOLD, smallFont.size2D)
            val numFont   = smallFont.deriveFont(Font.BOLD, JBUI.scaleFontSize(9f).toFloat())

            // ── Connecting lines ──────────────────────────────────────────────
            g2.stroke = BasicStroke(1.5f)
            for (i in 0 until n - 1) {
                val x1 = offX + stepW * i + stepW / 2 + r + JBUI.scale(3)
                val x2 = offX + stepW * (i + 1) + stepW / 2 - r - JBUI.scale(3)
                g2.color = if (i < currentStep) doneCol else futureCol
                g2.drawLine(x1, circleY, x2, circleY)
            }

            // ── Circles and labels ────────────────────────────────────────────
            for (i in 0 until n) {
                val cx      = offX + stepW * i + stepW / 2
                val current = i == currentStep
                val done    = i < currentStep

                // Circle fill
                g2.stroke = BasicStroke(0f)
                g2.color = when {
                    current -> accentCol
                    done    -> doneCol
                    else    -> bgCol
                }
                g2.fillOval(cx - r, circleY - r, r * 2, r * 2)

                // Circle border
                g2.stroke = BasicStroke(1.5f)
                g2.color = when {
                    current -> accentCol
                    done    -> doneCol
                    else    -> futureCol
                }
                g2.drawOval(cx - r, circleY - r, r * 2, r * 2)

                // Step number inside circle
                g2.font = numFont
                val numStr = "${i + 1}"
                val nFm = g2.fontMetrics
                g2.color = if (current || done) bgCol else futureCol
                g2.drawString(numStr, cx - nFm.stringWidth(numStr) / 2, circleY + nFm.ascent / 2 - JBUI.scale(1))

                // Step label below
                g2.font  = if (current) boldFont else smallFont
                g2.color = when {
                    current -> accentCol
                    done    -> labelCol
                    else    -> futureCol
                }
                val lFm = g2.fontMetrics
                g2.drawString(steps[i], cx - lFm.stringWidth(steps[i]) / 2, labelY)
            }
        } finally {
            g2.dispose()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the step index under pixel x, or -1 if outside the drawable area. */
    private fun stepIndexAt(x: Int): Int {
        if (steps.isEmpty()) return -1
        val ins   = insets
        val drawW = width - ins.left - ins.right
        val stepW = drawW / steps.size
        val i     = (x - ins.left) / stepW
        return i.coerceIn(0, steps.lastIndex)
    }

    private fun updateCursor(x: Int) {
        cursor = if (onStepClicked != null && stepIndexAt(x) in steps.indices)
            java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        else
            java.awt.Cursor.getDefaultCursor()
    }
}

