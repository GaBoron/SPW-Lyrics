package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.domain.CandidateScore
import dev.gaboron.spwlyrics.domain.LyricsSource
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

object ManualSearchWindow {
    private val sourceChoices = listOf<LyricsSource?>(null) + LyricsSource.entries.filter { it != LyricsSource.LOCAL }
    @Volatile private var activeDialog: JDialog? = null

    fun open() = SwingUtilities.invokeLater {
        activeDialog?.takeIf { it.isDisplayable }?.let {
            it.toFront()
            it.requestFocus()
            return@invokeLater
        }
        val query = PluginRuntime.currentQuery()
        if (query == null) {
            JOptionPane.showMessageDialog(null, "请先播放一首歌曲。", "SPW Lyrics", JOptionPane.INFORMATION_MESSAGE)
            return@invokeLater
        }

        val dialog = JDialog(null as Window?, "SPW Lyrics - 手动搜索", Dialog.ModalityType.APPLICATION_MODAL)
        activeDialog = dialog
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                if (activeDialog === dialog) activeDialog = null
            }
        })
        val keywords = JTextField(query.searchQueries().firstOrNull().orEmpty(), 42)
        val sources = JComboBox(sourceChoices.map { it?.displayName ?: "全部在线来源" }.toTypedArray())
        val search = JButton("搜索")
        val status = JLabel("可修改关键词并选择来源")
        val model = CandidateTableModel()
        val table = JTable(model).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            autoCreateRowSorter = true
        }
        val preview = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
        val apply = JButton("应用所选歌词").apply { isEnabled = false }
        val local = JButton("切回本地歌词")
        val automatic = JButton("恢复自动匹配")
        val close = JButton("关闭")

        fun selected(): CandidateScore? {
            val row = table.selectedRow.takeIf { it >= 0 } ?: return null
            return model.get(table.convertRowIndexToModel(row))
        }

        fun runSearch() {
            search.isEnabled = false
            status.text = "正在搜索…"
            val source = sourceChoices[sources.selectedIndex]
            CompletableFuture.supplyAsync { PluginRuntime.searchManual(keywords.text.trim(), source) }
                .whenComplete { rows, error -> SwingUtilities.invokeLater {
                    search.isEnabled = true
                    if (error != null) {
                        status.text = "搜索失败：${error.cause?.message ?: error.message}"
                    } else {
                        model.replace(rows.orEmpty())
                        status.text = "找到 ${rows?.size ?: 0} 个候选；自动匹配门槛不会因手动搜索而降低"
                    }
                } }
        }

        search.addActionListener { runSearch() }
        keywords.addActionListener { runSearch() }
        table.selectionModel.addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            val row = selected()
            apply.isEnabled = row != null
            preview.text = if (row == null) "" else "正在加载歌词预览…"
            row?.let { score ->
                CompletableFuture.supplyAsync { PluginRuntime.preview(score.candidate) }
                    .whenComplete { resolved, error -> SwingUtilities.invokeLater {
                        if (selected()?.candidate != score.candidate) return@invokeLater
                        preview.text = when {
                            error != null -> "预览失败：${error.cause?.message ?: error.message}"
                            resolved == null -> "该候选没有可用歌词。"
                            else -> resolved.document.lines.take(80).joinToString("\n") { line ->
                                buildString {
                                    append(line.text)
                                    line.translation?.let { append("\n  ↳ ").append(it) }
                                    line.romanization?.takeIf { line.translation == null }?.let { append("\n  ↳ ").append(it) }
                                }
                            }
                        }
                    } }
            }
        }
        apply.addActionListener {
            val row = selected() ?: return@addActionListener
            apply.isEnabled = false
            status.text = "正在应用歌词…"
            CompletableFuture.supplyAsync { PluginRuntime.applyManual(row.candidate) }
                .whenComplete { success, _ -> SwingUtilities.invokeLater {
                    apply.isEnabled = true
                    status.text = if (success == true) "歌词已应用并保存为当前歌曲的手动选择" else "应用失败：候选歌词不可用"
                } }
        }
        local.addActionListener {
            val success = PluginRuntime.useLocal()
            status.text = if (success) "已切回 SPW 的内嵌歌词/同名 .lrc 默认流程" else "当前没有正在播放的歌曲"
        }
        automatic.addActionListener {
            val success = PluginRuntime.useAutomatic()
            status.text = if (success) "已清除手动锁定，正在重新自动匹配" else "当前没有正在播放的歌曲"
        }
        close.addActionListener { dialog.dispose() }

        val controls = JPanel(FlowLayout(FlowLayout.LEADING)).apply {
            add(JLabel("关键词")); add(keywords); add(JLabel("来源")); add(sources); add(search)
        }
        val actions = JPanel(FlowLayout(FlowLayout.TRAILING)).apply {
            add(status); add(automatic); add(local); add(apply); add(close)
        }
        dialog.contentPane.add(controls, BorderLayout.NORTH)
        dialog.contentPane.add(
            JSplitPane(JSplitPane.VERTICAL_SPLIT, JScrollPane(table), JScrollPane(preview)).apply {
                resizeWeight = 0.52
            },
            BorderLayout.CENTER,
        )
        dialog.contentPane.add(actions, BorderLayout.SOUTH)
        dialog.minimumSize = Dimension(920, 620)
        dialog.setLocationRelativeTo(null)
        runSearch()
        dialog.isVisible = true
    }
}

private class CandidateTableModel : AbstractTableModel() {
    private val columns = arrayOf("来源", "歌曲", "艺术家", "专辑", "时长", "类型", "匹配分")
    private var rows: List<CandidateScore> = emptyList()

    fun replace(value: List<CandidateScore>) {
        rows = value
        fireTableDataChanged()
    }

    fun get(row: Int): CandidateScore? = rows.getOrNull(row)
    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val score = rows[rowIndex]
        val candidate = score.candidate
        return when (columnIndex) {
            0 -> candidate.source.displayName
            1 -> candidate.title
            2 -> candidate.artists.joinToString(" / ")
            3 -> candidate.album
            4 -> candidate.durationMs?.let { "%d:%02d".format(it / 60_000, it / 1_000 % 60) }.orEmpty()
            5 -> candidate.qualityHint?.let {
                when (it.rank) { 2 -> "逐字"; 1 -> "逐行"; else -> "普通" }
            }.orEmpty()
            else -> "%.3f".format(score.score)
        }
    }
}
