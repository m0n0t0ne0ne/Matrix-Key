package com.matrixkey.engine

/** A compact emoji set: categories for the panel and keywords for suggestions. */
object EmojiData {
    val categories: List<Pair<String, List<String>>> = listOf(
        "Smileys" to ("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 ☹️ 🤧 🫡 🫠").split(" "),
        "People" to ("👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 💪 🦾 👀 👁️ 👅 👄 🧠 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 👷 🤴 👸 👳 🧕 🤵 👰 🤰 🤱 👼 🎅 🦸 🦹 🧙 🧚 🧛 🧜 🧝 💃 🕺 👯 🧖 🧘 👫 👬 👭 💏 💑 👪 🫶").split(" "),
        "Animals" to ("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🦏 🐪 🦒 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🕊️ 🐇 🦝 🦨 🦡 🐁 🐀 🐿️ 🦔 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌸 🌺 🌻 🌹 🌷 🌼 💐 🌎 🌙 ⭐ 🌟 ✨ ⚡ 🔥 🌈 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 💧 🌊").split(" "),
        "Food" to ("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🧀 🥚 🍳 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 ☕ 🍵 🧃 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊").split(" "),
        "Activities" to ("⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️ 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🎗️ 🎫 🎟️ 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟️ 🎯 🎳 🎮 🕹️ 🧩 🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🚲 🛵 🏍️ ✈️ 🚀 🛸 🚁 ⛵ 🚢 🏠 🏡 🏢 🏥 🏦 🏫 🗽 🗼 🏰 🎡 🎢 🏖️ 🏝️ ⛺").split(" "),
        "Objects" to ("⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙️ ⏰ ⌛ 📡 🔋 🔌 💡 🔦 🕯️ 💸 💵 💰 💳 💎 ⚖️ 🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🔫 💣 🔪 🛡️ 🔮 💈 💊 💉 🩹 🧬 🌡️ 🧹 🧺 🧻 🚽 🛁 🔑 🗝️ 🚪 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎁 🎈 🎏 🎀 🎊 🎉 ✉️ 📩 📦 📫 📝 📁 📅 📌 📍 ✂️ 🖊️ ✏️ 🔍 🔒 🔓 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✅ ❌ ❓ ❗ 💯 🆗 🆒 🆕 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ ➡️ ⬅️ ⬆️ ⬇️ 🔄 ♻️ 🏁 🚩 🏳️ 🏴 💍 💥 💤 💦 🎶 🎵 🎃 💼 📚").split(" "),
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
        // people and family
        "👨" to "dad father man guy", "👩" to "mom mother woman lady", "👴" to "grandpa grandfather old",
        "👵" to "grandma grandmother old", "👶" to "baby kid newborn", "🧒" to "kid child", "👦" to "boy son kid",
        "👧" to "girl daughter kid", "👪" to "family parents kids", "👫" to "couple friends", "💑" to "couple love",
        "🤰" to "pregnant baby", "👼" to "angel baby", "🧔" to "beard man dad", "🤵" to "groom wedding suit",
        "👰" to "bride wedding", "💍" to "ring engaged wedding",
        // feelings
        "😀" to "happy smile grin", "😃" to "happy smile joy", "😄" to "happy smile laugh", "😁" to "happy grin smile",
        "😆" to "happy laugh lol", "😅" to "nervous sweat relief", "🙂" to "smile happy fine", "😊" to "happy smile blush glad",
        "😇" to "angel innocent", "🤩" to "excited star wow", "😋" to "yum tasty delicious", "😛" to "tongue silly",
        "😜" to "wink silly crazy", "🤪" to "crazy silly", "😏" to "smirk flirt", "😒" to "unamused meh annoyed",
        "😔" to "sad pensive down", "😞" to "sad disappointed", "😟" to "worried sad", "🙁" to "sad frown",
        "☹️" to "sad frown", "😣" to "struggle sad", "😩" to "weary tired sad", "😫" to "tired exhausted",
        "🥺" to "please puppy eyes sad cute", "😤" to "frustrated angry huff", "😳" to "embarrassed flushed shock",
        "🥵" to "hot sweat", "🥶" to "cold freezing", "😬" to "awkward grimace oops", "🤐" to "secret quiet zip",
        "🤫" to "shh quiet secret", "🤭" to "oops giggle", "🥱" to "yawn bored tired", "🤢" to "sick gross nausea",
        "🤮" to "sick vomit gross", "🤒" to "sick ill fever", "😷" to "sick mask ill", "🤧" to "sick sneeze",
        "😈" to "devil evil naughty", "🤡" to "clown joke", "👻" to "ghost boo halloween", "💩" to "poop poo",
        "🤓" to "nerd geek", "🫡" to "salute respect", "🫠" to "melting", "😐" to "neutral meh", "😑" to "meh blank",
        "🙃" to "upside down sarcasm", "😉" to "wink", "😌" to "relieved calm", "🤤" to "drool hungry",
        // hands
        "👌" to "ok okay perfect", "✌️" to "peace victory", "🤞" to "luck fingers crossed hope", "🤙" to "call me shaka",
        "👊" to "fist bump", "✊" to "fist power", "🤝" to "handshake deal agree", "🙌" to "yay celebrate praise hooray",
        "👉" to "point right", "👈" to "point left", "☝️" to "point up one", "🫶" to "heart hands love",
        // hearts and symbols
        "💔" to "broken heart sad breakup", "💕" to "love hearts", "💖" to "love sparkle heart", "💙" to "blue heart",
        "💚" to "green heart", "💛" to "yellow heart", "💜" to "purple heart", "🖤" to "black heart", "🧡" to "orange heart",
        "🤍" to "white heart", "✨" to "sparkle magic shine", "⭐" to "star", "🌟" to "star glow", "💥" to "boom bang",
        "💤" to "sleep zzz", "💦" to "sweat water", "🎶" to "music notes song", "🎵" to "music note song",
        // things and places
        "🏠" to "home house", "🚗" to "car drive", "✈️" to "plane flight travel trip", "🏖️" to "beach vacation holiday",
        "🎁" to "gift present", "🎄" to "christmas tree xmas", "🎃" to "halloween pumpkin", "💼" to "work job business",
        "📚" to "books study school read", "✏️" to "pencil write school", "💻" to "laptop computer work",
        "📱" to "phone mobile call text", "⏰" to "alarm clock time late", "💸" to "money spend pay", "💰" to "money rich cash",
        "🏆" to "trophy win winner champion", "⚽" to "soccer football ball", "🏀" to "basketball ball", "🏈" to "football",
        "🍔" to "burger food hamburger", "🍟" to "fries food", "🌮" to "taco food", "🍣" to "sushi food", "🍦" to "ice cream",
        "🍩" to "donut", "🍪" to "cookie", "🍫" to "chocolate", "🍷" to "wine drink", "🍾" to "champagne celebrate",
        "🥂" to "cheers toast celebrate", "☀️" to "sun sunny weather", "🌧️" to "rain rainy weather", "❄️" to "snow cold winter",
        "🌈" to "rainbow pride", "🌙" to "moon night", "🌹" to "rose flower love", "🌸" to "flower blossom spring",
    )

    /** Group words: searching "fruit" or "vegetables" shows every one of them. */
    private val groups: Map<String, String> = mapOf(
        "fruit fruits" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑",
        "vegetable vegetables veggie veggies veg" to "🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠",
        "food meal eat" to "🍞 🥐 🥯 🥖 🧀 🥚 🍳 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡",
        "dessert desserts sweet sweets candy snack" to "🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪",
        "drink drinks beverage" to "🥛 ☕ 🍵 🧃 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊",
        "alcohol booze" to "🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾",
        "animal animals pet pets" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🦏 🐪 🦒 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🕊️ 🐇 🦝 🦨 🦡 🐁 🐀 🐿️ 🦔",
        "bird birds" to "🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦃 🦚 🦜 🦢 🕊️ 🐓",
        "fish sea ocean" to "🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🌊",
        "bug bugs insect insects" to "🐝 🐛 🦋 🐌 🐞 🐜 🕷️",
        "plant plants flower flowers nature tree" to "🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌸 🌺 🌻 🌹 🌷 🌼 💐",
        "weather sky" to "☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 🌈 ⚡ 💧 🌊 🌙 ⭐ 🌟",
        "sport sports ball game" to "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 ⛳ 🏹 🎣 🥊 🥋 🛹 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️ 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🏄 🏊 🤽 🚣 🧗 🚵 🚴",
        "vehicle vehicles transport travel" to "🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🚲 🛵 🏍️ ✈️ 🚀 🛸 🚁 ⛵ 🚢",
        "place places building" to "🏠 🏡 🏢 🏥 🏦 🏫 🗽 🗼 🏰 🎡 🎢 🏖️ 🏝️ ⛺",
        "heart hearts love" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 🫶",
        "hand hands gesture" to "👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🫶",
        "music instrument" to "🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎵 🎶",
        "money cash pay" to "💸 💵 💰 💳 💎",
        "tech computer device" to "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙️",
        "cat cats" to "🐱 🐈 😺 😸 😹 😻 😼 😽 🙀 😿 😾",
        "monkey monkeys" to "🐵 🙈 🙉 🙊",
        "laugh laughing" to "😂 🤣 😆 😹",
        "angry mad" to "😤 😡 😠 🤬 👿",
        "sad crying" to "😢 😭 😔 😞 😟 🙁 ☹️ 😥 😿 💔",
        "party celebrate celebration" to "🥳 🎉 🎊 🎈 🍾 🥂 🎂 🎁",
        "christmas xmas holiday" to "🎄 🎅 ❄️ ☃️ 🎁",
    )

    /** Every emoji with the words it can be found by (its Unicode name plus common words). */
    private val index: List<Pair<String, List<String>>> by lazy {
        val aliasesFor = HashMap<String, MutableList<String>>()
        keywords.forEach { (word, list) -> list.forEach { e -> aliasesFor.getOrPut(e) { ArrayList() } += word } }
        extraAliases.forEach { (e, words) -> aliasesFor.getOrPut(e) { ArrayList() } += words.split(' ') }
        groups.forEach { (names, list) -> list.split(' ').forEach { e -> aliasesFor.getOrPut(e) { ArrayList() } += names.split(' ') } }
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
        return hits.sortedByDescending { it.second }.map { it.first }.take(150)
    }
}
