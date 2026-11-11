package dev.gaboron.spwlyrics.integration.metadataui

import dev.gaboron.spwlyrics.integration.tutorialui.TutorialStep
import dev.gaboron.spwlyrics.integration.tutorialui.TutorialTarget

internal enum class MetadataTutorialTarget : TutorialTarget {
    FILE, COVER, FIELDS, ONLINE, IMPORT, IMPORT_PREVIEW, LYRICS, LYRIC_ACTIONS, LINE, WORDS, TIMING_CONTROLS, FORMAT, SAVE
}

/** A read-only tour: steps select presentation only, never editor commands or draft data. */
internal enum class MetadataTutorialStep(
    override val title: String,
    override val detail: String,
    override val target: MetadataTutorialTarget,
    val view: MetadataLyricsView = MetadataLyricsView.STRUCTURED,
    val fields: Boolean = false,
) : TutorialStep {
    OPEN("先确认正在编辑哪首歌",
        "这里是歌曲读取区。正式编辑时，点击“打开歌曲”选择文件，或点“读取当前歌曲”。顶部路径就是保存目标，切歌不会自动更换它。\n\n现在展示的是虚拟歌曲《星光来信》。跟随气泡认识控件，完成后会回到你的真实草稿。",
        MetadataTutorialTarget.FILE),
    FIELDS("填写歌曲信息",
        "这里是可编辑的歌曲标签。点击输入框即可修改，例如将示例歌名“星光来信”换成正确名称。歌手、作曲者等多值字段每行填写一个值。\n\n宽页面可收起左侧信息栏；窄页面点击“歌曲信息 / 内嵌歌词”切换。",
        MetadataTutorialTarget.FIELDS, fields = true),
    COVER("选择、移除或恢复封面",
        "“选图”支持 JPEG / PNG，最多 20 MiB、2500 万像素。替换正面封面会保留其他图片；“移除”清除全部内嵌图片，“恢复”回到本次读取时的封面。所有修改先留在草稿中。",
        MetadataTutorialTarget.COVER, fields = true),
    ONLINE("从在线候选补充信息",
        "“在线匹配信息与封面”会带着当前文件的信息打开搜索。选中候选后点“应用元数据”，对照草稿勾选要替换的字段、封面和歌词。\n\n默认只补空项，也可选“仅补充 / 全替换”。“应用到草稿”后再核对保存；“取消导入”保留手动修改。",
        MetadataTutorialTarget.ONLINE),
    ONLINE_PREVIEW("勾选要导入的项目",
        "这是在线信息与当前草稿的对照页。点击字段前的勾选框决定是否替换；示例中只缺发行日期，所以默认勾选它。\n\n点“仅补充 / 全替换”可快速选择，再逐项调整。核对后点右下角“应用到草稿”；想返回就点“取消导入”。此时都还没有写入文件。",
        MetadataTutorialTarget.IMPORT_PREVIEW),
    IMPORT("先准备歌词内容",
        "“导入歌词”可读取 TTML、LRC、增强 LRC 或 SPL；也可从在线候选导入歌词，或到“编辑原文”粘贴内容。导入只更新草稿，不会立即改动歌曲文件。",
        MetadataTutorialTarget.IMPORT),
    LINES("添加、选择与排列歌词",
        "“逐句编辑”可在当前句后添加歌词、删除所选，并拖动把手调整顺序。调整顺序不会改变时间戳。\n\nCtrl 点选、Shift 连选、Ctrl+Shift 追加连选；列表内 Ctrl+A 全选，Delete 删除。主句拖动时会带上背景行。",
        MetadataTutorialTarget.LYRIC_ACTIONS),
    LINE("编辑一句歌词及演唱方式",
        "选择单句后，可改正文、翻译、音译、起止时间和主唱 / 对唱 / 背景人声。时间可填 mm:ss.mmm 或整数毫秒。\n\n−100 / +100 同时平移整句和逐字时间；多选后可批量平移、设置声部及背景。背景行归属前一主句。",
        MetadataTutorialTarget.LINE),
    WORDS("保留逐字时间来修改文字",
        "含逐字内容的单句会显示“逐字时间与文本”，可逐词改文字和起止时间。替换整句正文会清除该句逐字时间。\n\n含注音等嵌套标记的词，在逐字区只调时间，文字请到“编辑原文”修改。没有歌词或逐字内容时，这些控件暂不显示。",
        MetadataTutorialTarget.WORDS),
    SOURCE("需要时直接编辑原文",
        "“编辑原文”用于粘贴或修改歌词源文本，也可处理 TTML 的注音、扩展标记和跨分组移动。解析错误会显示在页面下方；修正后再保存。清空原文并保存会清除内嵌歌词。",
        MetadataTutorialTarget.LYRICS, MetadataLyricsView.SOURCE),
    PREVIEW("用预览检查结果",
        "切到“歌词预览”，核对歌词顺序、正文、翻译、音译与时间。预览读取当前草稿；如果发现错误，可随时回到逐句编辑或原文继续修改。",
        MetadataTutorialTarget.LYRICS, MetadataLyricsView.PREVIEW),
    TIMING("跟随播放制作逐字时间轴",
        "进入“打轴”后，让 SPW 播放正在编辑的文件。需要时先“自动分词”，再点一个词或“启用键盘”。\n\nF 记词首，G 结束当前词并开始下一词，H 只记词尾；Space 播放 / 暂停，A / D 换词，W / S 换句。录入仍只修改草稿。",
        MetadataTutorialTarget.TIMING_CONTROLS, MetadataLyricsView.TIMING),
    TIMING_TOOLS("用辅助工具细调时间",
        "“更多”中可撤销 / 重做、定位所选或跳过本句；也支持 Ctrl+Z / Ctrl+Y。“打轴设置”可调整判定方式、补偿并校准输入延迟。\n\n“频谱”需启用 SPW Media Runtime，可拖动词块或边缘调整时间。离开打轴会恢复进入前的播放倍速。",
        MetadataTutorialTarget.TIMING_CONTROLS, MetadataLyricsView.TIMING),
    FORMAT("先选择歌词写入格式",
        "这是“写入格式”菜单。点击它展开选项，再点 TTML、LRC、增强 LRC 或 SPL。下次编辑会记住上次选择。\n\nTTML 可保留完整逐字和扩展信息；LRC 会把逐字时间降为逐行，其他格式也有各自限制。若有信息损失，保存前会先列出供你确认。",
        MetadataTutorialTarget.FORMAT),
    SAVE("核对后写入歌曲文件",
        "核对顶部路径和当前草稿后，点击“写入歌曲文件”，或按 Ctrl+S。出现格式损失提示时，可继续写入、改选格式或返回编辑。\n\n写入才会修改音频。保存后歌词会刷新，歌曲标签和封面可重新扫描 SPW 曲库更新。",
        MetadataTutorialTarget.SAVE),
    RELOAD("重新读取与下次查看",
        "“重新读取”或 F5 重载顶部路径的文件。有未保存修改时，可保存并继续、放弃修改或继续编辑。\n\n教程完成或跳过后不再自动出现；想重看可在插件配置中点击“重置全部教程”。现在可以按自己的需要开始编辑了。",
        MetadataTutorialTarget.FILE),
}
