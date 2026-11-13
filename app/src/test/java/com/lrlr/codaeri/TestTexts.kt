package com.lrlr.codaeri

import com.lrlr.codaeri.domain.Texts
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 단위 시험에서 문구 창구를 src/main/res/values/strings*.xml 로 잇는다. 앱과 같은 문장이 나온다. */
object TestTexts {
    private val byName: Map<String, String> by lazy { load() }
    private val nameById: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    fun install() {
        Texts.resolver = { id, args ->
            val name = nameById[id] ?: error("R.string id $id 없음")
            val raw = byName[name] ?: error("strings*.xml 에 $name 없음")
            if (args.isEmpty()) raw else String.format(java.util.Locale.KOREA, raw, *args)
        }
    }

    private fun load(): Map<String, String> {
        val dir = listOf(File("src/main/res/values"), File("app/src/main/res/values")).first { it.isDirectory }
        val out = HashMap<String, String>()
        dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.sorted().forEach { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                val name = n.attributes.getNamedItem("name").nodeValue
                check(out.put(name, unescape(n.textContent)) == null) { "중복 키 $name" }
            }
        }
        return out
    }

    private fun unescape(s: String): String {
        val t = s.trim().let { if (it.length >= 2 && it.startsWith("\"") && it.endsWith("\"")) it.substring(1, it.length - 1) else it }
        val b = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length) {
                when (val n = t[i + 1]) { 'n' -> b.append('\n'); 't' -> b.append('\t'); else -> b.append(n) }
                i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }
}
