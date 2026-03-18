package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.util.WizardColors
import com.intellij.ui.JBColor
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
 * Compact horizontal step-progress indicator drawn above the wizard card area.
 *
 * Layout (per step):
 *  ● — completed (filled green circle)
 *  ◉ — current   (filled accent circle, bold label)
 *  ○ — future    (grey outline, grey label)
 *
 * The **entire cell column** is the click target, not just the circle.
 * A subtle tinted background is painted over the hovered cell so the full
 * hit area is visually communicated to the user.
 *
 * Clicking a cell invokes [onStepClicked] so callers can drive navigation.
 * All sizes are HiDPI-aware via [JBUI.scale].
 */
class WizardStepBar(val steps: List<String>) : JPanel() {

    /** Index of the currently active step (0-based). Triggers repaint on change. */
    var currentStep: Int = 0
        set(value) {
            field = value.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
            repaint()
        }

    /** Index of the step cell the pointer is currently over, or -1 when outside. */
    private var hoveredStep: Int = -1

    /**
     * Invoked when the user clicks a step cell.
     * The caller is responsible for actually switching steps (and running validation).
     */
    var onStepClicked: ((Int) -> Unit)? = null

    /**
     * Subtle tinted background painted over a hovered cell to communicate that
     * the full cell width — not just the circle — is the clickable area.
     */
    private val hoverBgColor: Color = JBColor(Color(0xE5EDF9), Color(0x404858))

    init {
        isOpaque = false
        border = JBUI.Borders.empty(8, 20, 6, 20)

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val cb = onStepClicked ?: return
                val i = stepIndexAt(e.x)
                if (i in steps.indices) cb(i)
            }
            override fun mouseExited(e: MouseEvent) {
                hoveredStep = -1
                repaint()
                cursor = java.awt.Cursor.getDefaultCursor()
            }
        })

        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val newHovered = if (onStepClicked != null) stepIndexAt(e.x) else -1
                if (newHovered != hoveredStep) {
                    hoveredStep = newHovered
                    repaint()
                }
                updateCursor(e.x)
            }
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

            val r       = JBUI.scale(10)                   // slightly larger for easier targeting
            val circleY = ins.top + r
            val labelY  = circleY + r + JBUI.scale(13)

            val accentCol: Color = WizardColors.accent
            val doneCol:   Color = WizardColors.success
            val futureCol: Color = UIUtil.getContextHelpForeground()
            val bgCol:     Color = UIUtil.getPanelBackground()
            val labelCol:  Color = UIUtil.getLabelForeground()

            val smallFont = JBUI.Fonts.smallFont()
            val boldFont  = smallFont.deriveFont(Font.BOLD, smallFont.size2D)
            val numFont   = smallFont.deriveFont(Font.BOLD, JBUI.scaleFontSize(9f).toFloat())

            // ── Hover cell background (drawn first, behind everything else) ───
            if (hoveredStep >= 0 && onStepClicked != null) {
                val pad = JBUI.scale(3)
                val hx  = offX + stepW * hoveredStep + pad
                val hy  = JBUI.scale(2)
                val hw  = stepW - pad * 2
                val hh  = height - JBUI.scale(4)
                g2.color = hoverBgColor
                g2.stroke = BasicStroke(0f)
                g2.fillRoundRect(hx, hy, hw, hh, JBUI.scale(6), JBUI.scale(6))
            }

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

    /** Returns the step index for the cell containing pixel [x]. */
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
