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

    /** Simple name search for the emoji panel. */
    fun search(query: String): List<String> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        return keywords.filterKeys { it.startsWith(q) }.values.flatten().distinct()
    }
}
