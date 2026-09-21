package app.daybricks.planner.ui.templates

import android.os.Build
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import java.util.Locale

data class ActivityEmoji(val value: String, val keywords: String)

object ActivityEmojiCatalog {
    private val groups = listOf(
        "🏃 run running jog бег пробежка спорт|🚶 walk walking прогулка ходьба|🥾 hike hiking поход треккинг|🚴 bike cycling велосипед|🏊 swim swimming плавание|🏋️ gym weights workout тренировка зал|🤸 gymnastics stretch гимнастика растяжка|🧘 yoga meditate relax йога медитация|⚽ football soccer футбол|🏀 basketball баскетбол|🏐 volleyball волейбол|🎾 tennis теннис|🏓 table tennis ping pong пинг понг|🏸 badminton бадминтон|🥊 boxing бокс|🥋 martial arts единоборства|⛷️ ski skiing лыжи|🏂 snowboard сноуборд|⛸️ skate skating коньки|🛹 skateboard скейтборд|🏄 surf серфинг|🧗 climb climbing скалолазание|🤾 handball гандбол|🏌️ golf гольф|🤺 fencing фехтование|🎯 target goal цель|🏆 achievement trophy победа|🥇 medal result результат",
        "💼 work job работа|💻 computer code coding компьютер код|🖥️ desktop office монитор офис|⌨️ typing write печать|📱 phone mobile телефон|📧 email почта письмо|📞 call звонок|🎥 video meeting видеовстреча|🤝 meeting deal встреча|📊 analytics chart отчёт аналитика|📈 growth progress рост|📉 decline costs снижение|🧮 accounting math расчёты|📋 tasks clipboard задачи|📝 note writing заметка|✍️ write writing писать|📌 pin important важное|📎 attachment вложение|🗂️ organize files организация|📁 folder project проект|📅 calendar plan календарь план|⏰ alarm deadline будильник дедлайн|⏳ focus timer фокус таймер|✅ done check готово|💡 idea creative идея|🔧 repair maintenance ремонт|🛠️ tools build инструменты|⚙️ settings process настройки|🔬 research science исследование|📐 design ruler дизайн|🎨 art design искусство|📸 photo фотография|🎙️ record podcast запись|🎧 audio listen аудио",
        "📚 study learn books учёба книги|📖 read reading чтение|🎓 course university курс университет|🏫 school школа|🧠 think brain thinking думать|🧩 puzzle problem задача|🔤 language letters язык|🗣️ speaking conversation речь|🎹 piano music пианино музыка|🎸 guitar гитара|🎻 violin скрипка|🥁 drums барабаны|🎤 sing singing пение|🎭 theater drama театр|🧵 sew sewing шитьё|🧶 knit knitting вязание|🪡 craft needle рукоделие|🪴 plants gardening растения|🌱 grow learn рост|🔭 astronomy stars астрономия|🧪 experiment chemistry эксперимент|🌍 geography world география|🏛️ history museum история",
        "❤️ health love сердце здоровье|🩺 doctor health врач|💊 medicine pills лекарство|🦷 dentist tooth зубы|👁️ eye vision зрение|😴 sleep сон|🛏️ bed rest отдых|🌙 night evening вечер|☀️ morning sun утро|🚿 shower душ|🛁 bath ванна|🧴 skincare уход кожа|💇 haircut парикмахер|💅 manicure ногти|🧹 clean cleaning уборка|🧽 wash мыть|🧺 laundry стирка|👕 clothes одежда|🛒 shopping groceries покупки|🛍️ shopping магазин|🍳 cook cooking готовить|🥗 healthy food salad еда салат|🍽️ meal dinner обед ужин|☕ coffee break кофе перерыв|💧 water drink вода|🧘‍♀️ mindfulness спокойствие|🌿 wellness nature здоровье природа",
        "👨‍👩‍👧‍👦 family семья|👶 child baby ребёнок|🐕 dog walk собака|🐈 cat pet кот питомец|🏠 home house дом|🚗 drive car машина|🚌 bus автобус|🚇 metro subway метро|🚆 train поезд|✈️ travel flight путешествие самолёт|🧳 trip luggage поездка|🗺️ route map маршрут|📍 place location место|⛽ fuel gas заправка|🔋 charge battery зарядка|🔑 keys ключи|💰 finance money деньги|💳 pay payment оплата|🧾 receipt bills счета|🏦 bank банк|📦 delivery package посылка|🎁 gift present подарок|🎂 birthday день рождения|🎉 party праздник|🎄 christmas рождество|🎃 halloween хэллоуин|💬 chat talk общение|📨 message сообщение|🙏 gratitude prayer благодарность|🤗 friends hug друзья",
        "🌳 outdoors park парк|🌲 forest лес|🏔️ mountain гора|🏖️ beach пляж|🏕️ camping лагерь|🌻 garden сад|🌅 sunrise рассвет|🌇 sunset закат|🌧️ rain дождь|❄️ snow снег|🔥 fire костёр|⭐ favorite star избранное|✨ special sparkle особое|🚀 launch старт запуск|🧭 direction compass направление|🔔 reminder напоминание|🎮 game игры|🎲 board game настольная игра|♟️ chess шахматы|🎬 movie кино|📺 television сериал|🎵 music музыка|🎧 podcast подкаст|🕹️ gaming игра|🎣 fishing рыбалка|🧑‍🍳 cooking chef готовка|🕯️ quiet candle тишина|🕐 time время|🔁 routine repeat рутина|➕ other add другое"
    )

    val all: List<ActivityEmoji> = groups.flatMap { group ->
        group.split('|').map { entry ->
            val firstSpace = entry.indexOf(' ')
            ActivityEmoji(entry.substring(0, firstSpace), entry.substring(firstSpace + 1))
        }
    }.distinctBy(ActivityEmoji::value)

    val featured: List<ActivityEmoji> = listOf(
        "🏃", "🚶", "🚴", "🏊", "🏋️", "🧘", "💼", "💻", "📚", "📖",
        "🧹", "🍳", "❤️", "😴", "🏠", "🚗", "🐕", "🎨", "🎵", "🌳",
        "☕", "📞", "📅", "✅", "🎯", "✈️", "🛒", "🩺", "🧠", "✨",
    ).mapNotNull { value -> all.firstOrNull { it.value == value } }

    fun search(query: String, locale: Locale = Locale.getDefault()): List<ActivityEmoji> {
        val needle = query.trim().lowercase(locale)
        if (needle.isBlank()) return featured
        return all.mapNotNull { item ->
            val unicodeName = item.value.codePoints().toArray().joinToString(" ") { codePoint ->
                runCatching { UCharacter.getName(codePoint) }.getOrNull().orEmpty()
            }
            val haystack = "${item.value} ${item.keywords} $unicodeName".lowercase(locale)
            val score = when {
                item.value == needle -> 0
                item.keywords.split(' ').any { it == needle } -> 1
                haystack.contains(needle) -> 2
                else -> return@mapNotNull null
            }
            score to item
        }.sortedWith(compareBy<Pair<Int, ActivityEmoji>> { it.first }.thenBy { it.second.keywords })
            .map { it.second }
    }

    fun singleEmojiOrNull(input: String): String? {
        val value = input.trim()
        if (value.isBlank() || value.length > 32) return null
        val codePoints = value.codePoints().toArray()
        val visible = codePoints.filterNot { it == 0xFE0F || it == 0x200D || it in 0x1F3FB..0x1F3FF }
        if (visible.isEmpty()) return null
        val emojiBases = visible.count { codePoint ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)
            } else {
                codePoint >= 0x203C
            }
        }
        val iterator = BreakIterator.getCharacterInstance().apply { setText(value) }
        iterator.first()
        val firstClusterEnd = iterator.next()
        val isSingleCluster = firstClusterEnd != BreakIterator.DONE && iterator.next() == BreakIterator.DONE
        return value.takeIf { emojiBases >= 1 && isSingleCluster }
    }
}
