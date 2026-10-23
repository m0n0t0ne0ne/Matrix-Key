package com.matrixkey.engine

/** A compact emoji set: categories for the panel and keywords for suggestions. */
object EmojiData {
    val categories: List<Pair<String, List<String>>> = listOf(
        "Smileys" to ("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾").split(" "),
        "People" to ("👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 💪 🦾 👀 👁️ 👅 👄 🧠 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 👷 🤴 👸 👳 🧕 🤵 👰 🤰 🤱 👼 🎅 🦸 🦹 🧙 🧚 🧛 🧜 🧝 💃 🕺 👯 🧖 🧘 👫 👬 👭 💏 💑 👪").split(" "),
        "Animals" to ("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🦏 🐪 🦒 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🕊️ 🐇 🦝 🦨 🦡 🐁 🐀 🐿️ 🦔 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌸 🌺 🌻 🌹 🌷 🌼 💐 🌎 🌙 ⭐ 🌟 ✨ ⚡ 🔥 🌈 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 💧 🌊").split(" "),
        "Food" to ("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🧀 🥚 🍳 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 ☕ 🍵 🧃 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊").split(" "),
        "Activities" to ("⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️ 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🎗️ 🎫 🎟️ 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟️ 🎯 🎳 🎮 🕹️ 🧩 🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🚲 🛵 🏍️ ✈️ 🚀 🛸 🚁 ⛵ 🚢 🏠 🏡 🏢 🏥 🏦 🏫 🗽 🗼 🏰 🎡 🎢 🏖️ 🏝️ ⛺").split(" "),
        "Objects" to ("⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙️ ⏰ ⌛ 📡 🔋 🔌 💡 🔦 🕯️ 💸 💵 💰 💳 💎 ⚖️ 🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🔫 💣 🔪 🛡️ 🔮 💈 💊 💉 🩹 🧬 🌡️ 🧹 🧺 🧻 🚽 🛁 🔑 🗝️ 🚪 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎁 🎈 🎏 🎀 🎊 🎉 ✉️ 📩 📦 📫 📝 📁 📅 📌 📍 ✂️ 🖊️ ✏️ 🔍 🔒 🔓 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✅ ❌ ❓ ❗ 💯 🆗 🆒 🆕 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ ➡️ ⬅️ ⬆️ ⬇️ 🔄 ♻️ 🏁 🚩 🏳️ 🏴").split(" "),
    )

    private val keywords: Map<String, List<String>> = mapOf(
        "love" to listOf("❤️", "😍", "🥰"), "heart" to listOf("❤️", "💕"), "happy" to listOf("😊", "😄", "🥳"),
        "lol" to listOf("😂", "🤣"), "haha" to listOf("😂", "🤣"), "funny" to listOf("😂"), "sad" to listOf("😢", "😭"),
        "cry" to listOf("😭"), "angry" to listOf("😠", "😡"), "mad" to listOf("😡"), "cool" to listOf("😎", "🆒"),
        "ok" to listOf("👌", "👍"), "okay" to listOf("👌", "👍"), "yes" to listOf("👍", "✅"), "no" to listOf("👎", "❌"),
        "thanks" to listOf("🙏", "😊"), "thank" to listOf("🙏"), "please" to listOf("🙏"), "fire" to listOf("🔥"),
        "party" to listOf("🥳", "🎉"), "birthday" to listOf("🎂", "🥳", "🎉"), "congrats" to listOf("🎉", "👏"),
        "dog" to listOf("🐶"), "cat" to listOf("🐱"), "pizza" to listOf("🍕"), "coffee" to listOf("☕"),
        "beer" to listOf("🍺"), "wine" to listOf("🍷"), "sun" to listOf("☀️"), "rain" to listOf("🌧️"),
        "sleep" to listOf("😴"), "tired" to listOf("😴", "🥱"), "think" to listOf("🤔"), "wow" to listOf("😮", "🤯"),
        "omg" to listOf("😱", "😮"), "kiss" to listOf("😘"), "hug" to listOf("🤗"), "money" to listOf("💰", "💸"),
        "car" to listOf("🚗"), "music" to listOf("🎵", "🎧"), "food" to listOf("🍔", "🍕"), "hungry" to listOf("🤤"),
        "good" to listOf("👍"), "great" to listOf("👍", "🙌"), "perfect" to listOf("💯", "👌"), "star" to listOf("⭐"),
        "sorry" to listOf("😔", "🙏"), "hi" to listOf("👋"), "hello" to listOf("👋"), "bye" to listOf("👋"),
        "cake" to listOf("🍰", "🎂"), "home" to listOf("🏠"), "work" to listOf("💼", "💻"), "game" to listOf("🎮"),
        "phone" to listOf("📱"), "gift" to listOf("🎁"), "christmas" to listOf("🎄", "🎅"), "flower" to listOf("🌸", "🌹"),
        "eyes" to listOf("👀"), "strong" to listOf("💪"), "clap" to listOf("👏"), "skull" to listOf("💀"), "dead" to listOf("💀"),
    )

    fun forWord(word: String): String? = keywords[word.lowercase()]?.firstOrNull()

    /** Extra search words people use that aren't in the official names. */
    private val extraAliases: Map<String, String> = mapOf(
        "😂" to "lol haha laugh crying funny", "🤣" to "lol rofl laugh funny", "😭" to "cry sob sad",
        "❤️" to "love heart red", "😍" to "love heart eyes crush", "🥰" to "love hearts adore",
        "👍" to "like yes ok good thumbs up", "👎" to "dislike no bad thumbs down", "🙏" to "please thanks pray hope",
        "🔥" to "fire lit hot", "💯" to "hundred perfect", "🥳" to "party celebrate birthday", "🎉" to "party congrats celebrate tada",
        "😎" to "cool sunglasses", "🤔" to "think hmm", "🙄" to "eye roll whatever", "😴" to "sleep tired zzz",
        "💀" to "dead skull dying", "👀" to "eyes look see", "🤷" to "shrug idk whatever", "🤦" to "facepalm",
        "😘" to "kiss love", "🤗" to "hug", "💪" to "strong muscle gym", "👏" to "clap applause bravo",
        "😢" to "sad tear", "😡" to "angry mad rage", "😱" to "scream omg shock", "🤯" to "mind blown wow",
        "👋" to "hi hello bye wave", "✅" to "check done yes", "❌" to "no wrong x cross", "☕" to "coffee tea",
        "🍕" to "pizza food", "🍺" to "beer drink", "🎂" to "birthday cake", "🐶" to "dog puppy", "🐱" to "cat kitty",
    )

    /** Every emoji with the words it can be found by (its Unicode name plus common words). */
    private val index: List<Pair<String, List<String>>> by lazy {
        val aliasesFor = HashMap<String, MutableList<String>>()
        keywords.forEach { (word, list) -> list.forEach { e -> aliasesFor.getOrPut(e) { ArrayList() } += word } }
        extraAliases.forEach { (e, words) -> aliasesFor.getOrPut(e) { ArrayList() } += words.split(' ') }
        categories.flatMap { it.second }.distinct().map { e ->
            val name = runCatching { Character.getName(e.codePointAt(0)) }.getOrNull().orEmpty().lowercase()
            val words = (name.split(' ', '-') + aliasesFor[e].orEmpty()).filter { it.isNotBlank() }
            e to words
        }
    }

    /** Search by name or common word: "heart", "thumbs up", "lol", "pizz". */
    fun search(query: String): List<String> {
        val tokens = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val hits = ArrayList<Pair<String, Int>>()
        index.forEachIndexed { order, (e, words) ->
            var score = 0
            for (t in tokens) {
                val best = words.maxOfOrNull { w -> if (w == t) 3 else if (w.startsWith(t)) 1 else 0 } ?: 0
                if (best == 0) { score = -1; break }
                score += best
            }
            if (score > 0) hits += e to (score * 1000 - order)
        }
        return hits.sortedByDescending { it.second }.map { it.first }.take(80)
    }
}
