package net.matsudamper.kmp.activitypub.frontend.screen.reaction

// emoji-data/generate_unicode_emojis.py で作る。手で書き換えない

/**
 * 1 行が 1 つの絵文字で、`絵文字<TAB>分類の番号<TAB>検索に使う名前を | で繋いだもの`。CLDR の並び
 */
internal val UNICODE_EMOJI_DATA: List<String> = listOf(
    UNICODE_EMOJI_DATA_0,
    UNICODE_EMOJI_DATA_1,
    UNICODE_EMOJI_DATA_2,
    UNICODE_EMOJI_DATA_3,
    UNICODE_EMOJI_DATA_4,
)

private const val UNICODE_EMOJI_DATA_0: String = """
😀	0	にっこり笑う|スマイル|ナイス|にっこり|ハッピー|笑う|笑顔|顔|grinning face|cheerful|cheery|face|grin|grinning|happy|laugh|nice|smile|smiling|teeth
😃	0	わーい|スマイル|ハッピー|口を開けた笑顔|笑顔|顔|grinning face with big eyes|awesome|big|eyes|face|grin|grinning|happy|mouth|open|smile|smiling|teeth|yay
😄	0	笑顔|スマイル|ハッピー|わーい|顔|grinning face with smiling eyes|eye|eyes|face|grin|grinning|happy|laugh|lol|mouth|open|smile|smiling
😁	0	にやっと笑う|スマイル|ナイス|ハッピー|笑う|笑顔|顔|beaming face with smiling eyes|beaming|eye|eyes|face|grin|grinning|happy|nice|smile|smiling|teeth
😆	0	きゃー|あはは|うけるね|スマイル|嬉しい|満足|笑|笑顔|顔|grinning squinting face|closed|eyes|face|grinning|haha|hahaha|happy|laugh|lol|mouth|open|rofl|smile|smiling|squinting
😅	0	冷や汗笑顔|スマイル|とほほ|冷や汗|汗|笑顔|顔|grinning face with sweat|cold|dejected|excited|face|grinning|mouth|nervous|open|smile|smiling|stress|stressed|sweat
🤣	0	笑い転げる|あはは|うけるね|スマイル|泣き笑い|涙|爆笑|笑う|笑顔|顔|rolling on the floor laughing|crying|face|floor|funny|haha|happy|hehe|hilarious|joy|laugh|lmao|lol|rofl|roflmao|rolling|tear
😂	0	嬉し泣き|あはは|スマイル|大笑い|泣き笑い|涙|爆笑|笑う|笑顔|顔|face with tears of joy|crying|face|feels|funny|haha|happy|hehe|hilarious|joy|laugh|lmao|lol|rofl|roflmao|tear
🙂	0	微笑む|スマイル|ハッピー|ほほえみ|微笑み|笑顔|顔|slightly smiling face|face|happy|slightly|smile|smiling
🙃	0	さかさまの顔|さかさま|笑顔|顔|upside-down face|face|hehe|smile|upside-down
🫠	0	溶けている顔|とろける|暑い|液体|溶けそう|溶ける|顔|melting face|disappear|dissolve|embarrassed|face|haha|heat|hot|liquid|lol|melt|melting|sarcasm|sarcastic
😉	0	ウインク|ウィンク|ウィンクする顔|顔|winking face|face|flirt|heartbreaker|sexy|slide|tease|wink|winking|winks
😊	0	にこにこ|スマイル|微笑み|満足|目を細める|頬を赤らめる|顔|smiling face with smiling eyes|blush|eye|eyes|face|glad|satisfied|smile|smiling
😇	0	天使の輪がついた笑顔|スマイル|ハッピー|天使の輪|平和|笑顔|顔|smiling face with halo|angel|angelic|angels|blessed|face|fairy|fairytale|fantasy|halo|happy|innocent|peaceful|smile|smiling|spirit|tale
🥰	0	ハートの笑顔|ハート|メロメロ|ラブ|夢中|愛してる|笑顔|顔|smiling face with hearts|3|adore|crush|face|heart|hearts|ily|love|romance|smile|smiling|you
😍	0	目がハートの笑顔|スマイル|ハート|ラブ|大好き|笑顔|顔|smiling face with heart-eyes|143|bae|eye|face|feels|heart-eyes|hearts|ily|kisses|love|romance|romantic|smile|xoxo
🤩	0	目が星の笑顔|スマイル|わーい|星|笑顔|顔|star-struck|excited|eyes|face|grinning|smile|star|starry-eyed|wow
😘	0	投げキッス|キス|ちゅっ|ハート|顔|face blowing a kiss|adorbs|bae|blowing|face|flirt|heart|ily|kiss|love|lover|miss|muah|romantic|smooch|xoxo|you
😗	0	キス|ちゅっ|デート|顔|kissing face|143|date|dating|face|flirt|ily|kiss|love|smooch|smooches|xoxo|you
☺️	0	スマイリー|スマイル|ハッピー|ほっ|笑顔|顔|smiling face|face|happy|outlined|relaxed|smile|smiling
😚	0	ちゅっ|キス|デート|目を閉じてキス|目を閉じる|顔|kissing face with closed eyes|143|bae|blush|closed|date|dating|eye|eyes|face|flirt|ily|kisses|kissing|smooches|xoxo
😙	0	にっこりキス|キス|スマイル|ちゅっ|笑顔|顔|kissing face with smiling eyes|143|closed|date|dating|eye|eyes|face|flirt|ily|kiss|kisses|kissing|love|night|smile|smiling
🥲	0	嬉し涙の顔|じーん|感動|感激|泣く|涙|顔|smiling face with tear|face|glad|grateful|happy|joy|pain|proud|relieved|smile|smiley|smiling|tear|touched
😋	0	にこにこぺろり|うまい|おいしい|おいしそう|スマイル|ぺろり|笑顔|腹ぺこ|顔|face savoring food|delicious|eat|face|food|full|hungry|savor|smile|smiling|tasty|um|yum|yummy
😛	0	舌を出した顔|べー|べろ|甘い|舌|顔|face with tongue|awesome|cool|face|nice|party|stuck-out|sweet|tongue
😜	0	あっかんべー|ウィンク|ジョーク|べー|冗談|舌|顔|winking face with tongue|crazy|epic|eye|face|funny|joke|loopy|nutty|party|stuck-out|tongue|wacky|weirdo|wink|winking|yolo
🤪	0	ふざけた顔|おかしい|ジョーク|ふざけ|顔|zany face|crazy|eye|eyes|face|goofy|large|small|zany
😝	0	目を閉じてべー|べー|べろ|わーい|味見|舌|顔|squinting face with tongue|closed|eye|eyes|face|gross|horrible|omg|squinting|stuck-out|taste|tongue|whatever|yolo
🤑	0	お金の顔|お金|べー|べろ|舌|顔|money-mouth face|face|money|money-mouth|mouth|paid
🤗	0	ハグ|スマイル|笑顔|顔|smiling face with open hands|face|hands|hug|hugging|open|smiling
🤭	0	口に手を当てた顔|おっと|クスクス|スマイル|内緒|笑顔|顔|face with hand over mouth|face|giggle|giggling|hand|mouth|oops|realization|secret|shock|sudden|surprise|whoops
🫢	0	目を開けて口に手を当てた顔|おそれ|おののき|びっくり|静かに|顔|驚き|face with open eyes and hand over mouth|amazement|awe|disbelief|embarrass|eyes|face|gasp|hand|mouth|omg|open|over|quiet|scared|shock|surprise
🫣	0	指の間からのぞき見る顔|こわごわ|ちらっ|のぞき見|盗み見|顔|face with peeking eye|captivated|embarrass|eye|face|hide|hiding|peek|peeking|peep|scared|shy|stare
🤫	0	しーっ|しー|シーッ|しっ|静かに|顔|shushing face|face|quiet|shh|shush|shushing
🤔	0	考える顔|うーん|考える|考え中|顎に手|顔|thinking face|chin|consider|face|hmm|ponder|pondering|thinking|wondering
🫡	0	敬礼する顔|オーケー|了解|敬礼|軍隊|顔|saluting face|face|good|luck|ma’am|ok|respect|salute|saluting|sir|troops|yes
🤐	0	口チャック|チャック|内緒|口|秘密|顔|zipper-mouth face|face|keep|mouth|quiet|secret|shut|zip|zipper|zipper-mouth
🤨	0	眉を上げた顔|不審|疑い|顔|驚き|face with raised eyebrow|disapproval|disbelief|distrust|emoji|eyebrow|face|hmm|mild|raised|skeptic|skeptical|skepticism|surprise|what
😐	0	ポーカーフェイス|無表情|笑えない|顔|neutral face|awkward|blank|deadpan|expressionless|face|fine|jealous|meh|neutral|oh|shade|straight|unamused|unhappy|unimpressed|whatever
😑	0	無表情|ふーん|やきもち|無感情|顔|expressionless face|awkward|dead|expressionless|face|fine|inexpressive|jealous|meh|not|oh|omg|straight|uh|unhappy|unimpressed|whatever
😶	0	口のない顔|だんまり|口なし|沈黙|無表情|無言|顔|face without mouth|awkward|blank|expressionless|face|mouth|mouthless|mute|quiet|secret|silence|silent|speechless
🫥	0	点線の顔|どうでもいい|ふーん|内向的|内気|憂鬱|落胆|顔|dotted line face|depressed|disappear|dotted|face|hidden|hide|introvert|invisible|line|meh|whatever|wtv
😶‍🌫️	0	雲の中の顔|うわの空|ぼんやり|夢中|放心状態|顔|face in clouds|absentminded|clouds|face|fog|head
😏	0	薄笑いをする顔|にやり|ふっ|顔|smirking face|boss|dapper|face|flirt|homie|kidding|leer|shade|slick|sly|smirk|smug|snicker|suave|suspicious|swag
😒	0	しらけた|ジト目|しらけ|しらける|つまらない|やきもち|不満|顔|unamused face|...|bored|face|fine|jealous|jel|jelly|pissed|smh|ugh|uhh|unamused|unhappy|weird|whatever
🙄	0	上を見る顔|あきれた|うーん|上目|目をむく|顔|face with rolling eyes|eyeroll|eyes|face|rolling|shade|ugh|whatever
😬	0	しかめ面|いー|しかめっ面|しかめ顔|顔|grimacing face|awk|awkward|dentist|face|grimace|grimacing|grinning|smile|smiling
😮‍💨	0	息を吐く顔|あきらめ|ため息|ほっとした|安心|息切れ|疲れた|顔|face exhaling|blow|blowing|exhale|exhaling|exhausted|face|gasp|groan|relief|sigh|smiley|smoke|whisper|whistle
🤥	0	うそつきの顔|ピノキオの顔|嘘|嘘つき|顔|lying face|face|liar|lie|lying|pinocchio
🫨	0	震えている顔|ガーン|ショック|なんてこった|パニック|ビックリ|まいった|地震|振動|震えている|顔|shaking face|crazy|daze|earthquake|face|omg|panic|shaking|shock|surprise|vibrate|whoa|wow
🙂‍↔️	0	首を横に振る|いいえ|不賛成|顔|首を振る|head shaking horizontally|head|horizontally|no|shake|shaking
🙂‍↕️	0	首を縦に振る|はい|頷く|顔|首を振る|head shaking vertically|head|nod|shaking|vertically|yes
😌	0	ほっとした顔|ほっ|安心|顔|relieved face|calm|face|peace|relief|relieved|zen
😔	0	しょぼーん|がっかり|しょんぼり|悲しい|考え込む顔|顔|pensive face|awful|bored|dejected|died|disappointed|face|losing|lost|pensive|sad|sucks
😪	0	眠い|寝る|疲れた|眠そうな顔|眠っている顔|眠る|顔|sleepy face|crying|face|good|night|sad|sleep|sleeping|sleepy|tired
🤤	0	よだれを垂らした顔|よだれ|顔|drooling face|drooling|face
😴	0	寝る|zzz|おやすみ|グーグー|寝顔|眠い|眠る|顔|sleeping face|bed|bedtime|face|good|goodnight|nap|night|sleep|sleeping|tired|whatever|yawn
😷	0	マスク顔|ドクター|マスク|医師|歯科医|病気|花粉症|顔|風邪|face with medical mask|cold|dentist|dermatologist|doctor|dr|face|germs|mask|medical|medicine|sick
🤒	0	熱がある顔|体温計|熱を測る|病気|顔|風邪|face with thermometer|face|ill|sick|thermometer
🤕	0	包帯を巻いた顔|ケガ|ケガしてる顔|包帯|痛い|顔|face with head-bandage|bandage|face|head-bandage|hurt|injury|ouch
🤢	0	吐き気を催している顔|具合の悪い顔|吐きそう|気持ち悪い|病気|顔|nauseated face|face|gross|nasty|nauseated|sick|vomit
🤮	0	嘔吐する顔|げろ|吐く|気持ち悪い|病気|顔|face vomiting|barf|ew|face|gross|puke|sick|spew|throw|up|vomit|vomiting
🤧	0	くしゃみする顔|くしゃみ|花粉症|顔|風邪|sneezing face|face|fever|flu|gesundheit|sick|sneeze|sneezing
🥵	0	暑い顔|暑さ|死にそう|汗|熱中症|発熱|舌を出した顔|赤い顔|hot face|dying|face|feverish|heat|hot|panting|red-faced|stroke|sweating|tongue
🥶	0	寒い顔|しもやけ|つらら|凍え|寒さ|氷点下|青|青い顔|顔|cold face|blue|blue-faced|cold|face|freezing|frostbite|icicles|subzero|teeth
🥴	0	ふらふらの顔|うつろな目|べろんべろん|ほろ酔い|めまい|中毒|woozy face|dizzy|drunk|eyes|face|intoxicated|mouth|tipsy|uneven|wavy|woozy
😵	0	めまい|ふらふら|疲れた|病気|顔|face with crossed-out eyes|crossed-out|dead|dizzy|eyes|face|feels|knocked|out|sick|tired
😵‍💫	0	目を回した顔|くらくら|ふらふら|めまい|渦巻き|目がぐるぐる|目が回る|face with spiral eyes|confused|dizzy|eyes|face|hypnotized|omg|smiley|spiral|trouble|whoa|woah|woozy
🤯	0	頭爆発|ショック|びっくり|ぼんっ|爆発|顔|驚き|exploding head|blown|explode|exploding|head|mind|mindblown|no|shocked|way
🤠	0	カウボーイの顔|カウガール|カウボーイ|帽子|顔|cowboy hat face|cowboy|cowgirl|face|hat
🥳	0	パーティーの顔|おめでとう|お祝い|パーティー|ハッピーバースデー|帽子|笑顔|笛|誕生日|partying face|bday|birthday|celebrate|celebration|excited|face|happy|hat|hooray|horn|party|partying
🥸	0	変装した顔|ひげ|メガネ|仮装|口ひげ|変装|顔|disguised face|disguise|eyebrow|face|glasses|incognito|moustache|mustache|nose|person|spy|tache|tash
😎	0	サングラスで笑顔|クール|サングラス|スマイル|眼鏡|笑顔|顔|smiling face with sunglasses|awesome|beach|bright|bro|chilling|cool|face|rad|relaxed|shades|slay|smile|style|sunglasses|swag|win
🤓	0	オタク|エキスパート|ガリ勉|マニア|メガネ|優等生|利口|知的|秀才|顔|nerd face|brainy|clever|expert|face|geek|gifted|glasses|intelligent|nerd|smart
🧐	0	モノクルを付けた顔|メガネ|上品|片眼鏡|考える|顔|face with monocle|classy|face|fancy|monocle|rich|stuffy|wealthy
😕	0	混乱|しかめ面|ふーん|わからない|困惑|顔|confused face|befuddled|confused|confusing|dunno|face|frown|hm|meh|not|sad|sorry|sure
🫤	0	口が斜めの顔|不信|不満|困惑|無気力|無関心|顔|face with diagonal mouth|confused|confusion|diagonal|disappointed|doubt|doubtful|face|frustrated|frustration|meh|mouth|skeptical|unsure|whatever|wtv
😟	0	悩む顔|困った|心配|悲しい|顔|worried face|anxious|butterflies|face|nerves|nervous|sad|stress|stressed|surprised|worried|worry
🙁	0	少し困った顔|しかめ面|困った|少し困った|顔|slightly frowning face|face|frown|frowning|sad|slightly
☹️	0	困った顔|しかめ面|困った|悲しい|顔|frowning face|face|frown|frowning|sad
😮	0	口が開いた顔|へー|信じられない|共感|顔|驚き|face with open mouth|believe|face|forgot|mouth|omg|open|shocked|surprised|sympathy|unbelievable|unreal|whoa|wow|you
😯	0	ぽかーん|すごいね|びっくり|びっくり顔|顔|驚き|hushed face|epic|face|hushed|omg|stunned|surprised|whoa|woah
😲	0	びっくり|えーっ|びっくりした顔|まさか|顔|驚き|驚愕|astonished face|astonished|cost|face|no|omg|shocked|totally|way
😳	0	赤面|えーっ|とまどい|赤くなる|顔|驚き|flushed face|amazed|awkward|crazy|dazed|dead|disbelief|embarrassed|face|flushed|geez|heat|hot|impressed|jeez|what|wow
🥺	0	訴えるような顔|お願い|ぴえん|大きな目|子犬の目|悲しみ|情け|懇願|顔|pleading face|begging|big|eyes|face|mercy|not|pleading|please|pretty|puppy|sad|why
🥹	0	涙をこらえた顔|うるうる|嬉し泣き|懇願|泣き顔|涙|顔|face holding back tears|admiration|aww|back|cry|embarrassed|face|feelings|grateful|gratitude|holding|joy|please|proud|resist|sad|tears
😦	0	あきれ顔|しかめ面|しかめ顔|ぽかーん|不本意|不満|口を開けた顔|呆然|顔|驚き|frowning face with open mouth|caught|face|frown|frowning|guard|mouth|open|scared|scary|surprise|what|wow
😧	0	苦悩|困った顔|苦悶|顔|驚き|anguished face|anguished|face|forgot|scared|scary|stressed|surprise|unhappy|what|wow
😨	0	青ざめ|がーん|不安|怖い|青ざめた顔|顔|fearful face|afraid|anxious|blame|face|fear|fearful|scared|worried
😰	0	冷や汗青ざめ|冷や汗|寒い|汗|青い顔|青ざめ|顔|anxious face with sweat|anxious|blue|cold|eek|face|mouth|nervous|open|rushed|scared|sweat|yikes
😥	0	どうしよう|困った|汗|顔|sad but relieved face|anxious|call|close|complicated|disappointed|face|not|relieved|sad|sweat|time|whew
😢	0	泣き顔|ひどい|寂しい|悲しい|泣く|涙|顔|crying face|awful|cry|crying|face|feels|miss|sad|tear|triste|unhappy
😭	0	大泣き|号泣|号泣する顔|悲しい|泣く|涙|顔|loudly crying face|bawling|cry|crying|face|loudly|sad|sob|tear|tears|unhappy
😱	0	恐怖|がーん|ショック|叫び|叫んでいる顔|顔|驚き|face screaming in fear|epic|face|fear|fearful|munch|scared|scream|screamer|screaming|shocked|surprised|woah
😖	0	困惑した顔|困った|困惑|混乱|顔|confounded face|annoyed|confounded|confused|cringe|distraught|face|feels|frustrated|mad|sad
😣	0	我慢|我慢する顔|頭痛|顔|persevering face|concentrate|concentration|face|focus|headache|persevere|persevering
😞	0	失望した顔|がっかり|がっくり|悲しい|残念|顔|disappointed face|awful|blame|dejected|disappointed|face|fail|losing|sad|unhappy
😓	0	冷や汗|さむけ|汗|肝を冷やした|頭痛|顔|downcast face with sweat|close|cold|downcast|face|feels|headache|nervous|sad|scared|sweat|yikes
😩	0	あきらめ|へとへと|泣き顔|疲れ|腹ぺこ|顔|weary face|crying|face|fail|feels|hungry|mad|nooo|sad|sleepy|tired|unhappy|weary
😫	0	疲れた|くしゃみ|疲れ|疲れた顔|限界|顔|tired face|cost|face|feels|nap|sad|sneeze|tired
🥱	0	あくびした顔|おやすみ|ふあ～|疲れ|眠い|退屈|顔|yawning face|bedtime|bored|face|goodnight|nap|night|sleep|sleepy|tired|whatever|yawn|yawning|zzz
😤	0	勝ち誇った顔|ふんっ|得意気|怒り|顔|鼻息|face with steam from nose|anger|angry|face|feels|fume|fuming|furious|fury|mad|nose|steam|triumph|unhappy|won
😡	0	ふくれっ面|かんかん|ぷんぷん|怒り|激怒|赤い顔|顔|enraged face|anger|angry|enraged|face|feels|mad|maddening|pouting|rage|red|shade|unhappy|upset
😠	0	ぷんぷん|不満|怒った|怒った顔|怒り|顔|angry face|anger|angry|blame|face|feels|frustrated|mad|maddening|rage|shade|unhappy|upset
🤬	0	ののしる|シンボルが口にある顔|ののしり|怒り|悪口|検閲|顔|face with symbols on mouth|censor|cursing|cussing|face|mad|mouth|pissed|swearing|symbols
😈	0	笑った悪魔|スマイル|デビル|悪魔|笑顔|角|顔|smiling face with horns|demon|devil|evil|face|fairy|fairytale|fantasy|horns|purple|shade|smile|smiling|tale
👿	0	怒った悪魔|かんかん|デビル|悪魔|激怒|角|顔|angry face with horns|angry|demon|devil|evil|face|fairy|fairytale|fantasy|horns|imp|mischievous|purple|shade|tale
💀	0	ドクロ|スカル|死|死者|頭蓋骨|顔|骸骨|skull|body|dead|death|face|fairy|fairytale|i’m|lmao|monster|tale|yolo
☠️	0	ドクロと骨|スカル|ドクロ|死|顔|骨|骸骨|skull and crossbones|bone|crossbones|dead|death|face|monster|skull
💩	0	うんち|うんこ|臭い|顔|pile of poo|bs|comic|doo|dung|face|fml|monster|pile|poo|poop|smelly|smh|stink|stinks|stinky|turd
🤡	0	ピエロの顔|ピエロ|道化師|顔|clown face|clown|face
👹	0	鬼|お面|なまはげ|化け物|怖い|顔|ogre|creature|devil|face|fairy|fairytale|fantasy|mask|monster|scary|tale
👺	0	天狗|お面|怒り|goblin|angry|creature|face|fairy|fairytale|fantasy|mask|mean|monster|tale
👻	0	お化け|ゴースト|ハロウィーン|ユーレイ|幽霊|怖い|ghost|boo|creature|excited|face|fairy|fairytale|fantasy|halloween|haunting|monster|scary|silly|tale
👽	0	エイリアン|ufo|ユーフォー|宇宙人|異星人|顔|alien|creature|extraterrestrial|face|fairy|fairytale|fantasy|monster|space|tale
👾	0	インベーダー|エイリアン|ゲーム|スペースインベーダー|モンスター|宇宙人|異星人|alien monster|alien|creature|extraterrestrial|face|fairy|fairytale|fantasy|game|gamer|games|monster|pixelated|space|tale|ufo
🤖	0	ロボット|ロボットの顔|顔|robot|face|monster
😺	0	にっこり笑う猫|スマイル|動物|猫|笑顔|顔|grinning cat|animal|cat|face|grinning|mouth|open|smile|smiling
😸	0	にやっと笑う猫|スマイル|動物|猫|笑う|笑顔|顔|grinning cat with smiling eyes|animal|cat|eye|eyes|face|grin|grinning|smile|smiling
😹	0	嬉し泣きする猫|スマイル|動物|涙|爆笑|猫|笑顔|顔|cat with tears of joy|animal|cat|face|joy|laugh|laughing|lol|tear|tears
😻	0	目がハートの猫|スマイル|ハート|動物|猫|笑顔|顔|smiling cat with heart-eyes|animal|cat|eye|face|heart|heart-eyes|love|smile|smiling
😼	0	にやりとする猫|にやり|ふっ|動物|猫|顔|cat with wry smile|animal|cat|face|ironic|smile|wry
😽	0	猫のキス|キス|ちゅっ|動物|猫|顔|kissing cat|animal|cat|closed|eye|eyes|face|kiss|kissing
🙀	0	絶望する猫|がーん|ショック|びっくり|動物|猫|顔|weary cat|animal|cat|face|oh|surprised|weary
😿	0	泣いている猫|動物|悲しい|泣く|涙|猫|顔|crying cat|animal|cat|cry|crying|face|sad|tear
😾	0	不機嫌な猫|ぷんぷん|不機嫌|動物|猫|顔|pouting cat|animal|cat|face|pouting
🙈	0	見ざる|三猿|動物|猿|目隠し|隠す|see-no-evil monkey|embarrassed|evil|face|forbidden|forgot|gesture|hide|monkey|no|omg|prohibited|scared|secret|smh|watch
🙉	0	聞かざる|三猿|猿|秘密|hear-no-evil monkey|animal|ears|evil|face|forbidden|gesture|hear|listen|monkey|no|not|prohibited|secret|shh|tmi
🙊	0	言わざる|三猿|内緒|黙る|猿|秘密|speak-no-evil monkey|animal|evil|face|forbidden|gesture|monkey|no|not|oops|prohibited|quiet|secret|speak|stealth
💌	0	ラブレター|ハート|バレンタインデー|封筒|恋文|手紙|love letter|heart|letter|love|mail|romance|valentine
💘	0	ハートに矢|キューピッド|ハート|矢|heart with arrow|143|adorbs|arrow|cupid|date|emotion|heart|ily|love|romance|valentine
💝	0	ハートにリボン|ハート|バレンタインデー|リボン|記念日|heart with ribbon|143|anniversary|emotion|heart|ily|kisses|ribbon|valentine|xoxo
💖	0	きらきらハート|きらきら|キラキラ|ハート|sparkling heart|143|emotion|excited|good|heart|ily|kisses|morning|night|sparkle|sparkling|xoxo
💗	0	大きくなるハート|ドキドキ|ときめき|ハート|growing heart|143|emotion|excited|growing|heart|heartpulse|ily|kisses|muah|nervous|pulse|xoxo
💓	0	ドキドキするハート|ドキドキ|ハート|動悸|鼓動|beating heart|143|beating|cardio|emotion|heart|heartbeat|ily|love|pulsating|pulse
💞	0	回転するハート|かわいい|ハート|revolving hearts|143|adorbs|anniversary|emotion|heart|hearts|revolving
💕	0	2つのハート|デート|ハート|ラブ|記念日|two hearts|143|anniversary|date|dating|emotion|heart|hearts|ily|kisses|love|loving|two|xoxo
💟	0	ハートデコ|ハート|ハートデコレーション|白いハート|紫のハート|heart decoration|143|decoration|emotion|heart|hearth|purple|white
❣️	0	ハートのびっくり|エクスクラメーションマーク|ハート|びっくりマーク|heart exclamation|bang|exclamation|heart|heavy|mark|punctuation
💔	0	割れたハート|ハート|ハートブレイク|別れ|別れた|壊れたハート|失恋|broken heart|break|broken|crushed|emotion|heart|heartbroken|lonely|sad
❤️‍🔥	0	燃えるハート|ハート|愛|火の付いた|炎|聖なる心臓|heart on fire|burn|fire|heart|love|lust|sacred
❤️‍🩹	0	包帯を巻いたハート|ケガ|ハート|傷ついた|傷心|包帯|mending heart|healthier|heart|improving|mending|recovering|recuperating|well
❤️	0	赤いハート|ハート|愛|赤|red heart|emotion|heart|love|red
🩷	0	ピンクのハート|かわいい|スペシャル|ハート|ピンク|ラブ|好き|愛|愛してる|気持ち|pink heart|143|adorable|cute|emotion|heart|ily|like|love|pink|special|sweet
🧡	0	オレンジのハート|オレンジ|ハート|orange heart|143|heart|orange
💛	0	黄色のハート|ハート|愛|黄色|黄色いハート|yellow heart|143|cardiac|emotion|heart|ily|love|yellow
💚	0	緑のハート|ハート|緑|green heart|143|emotion|green|heart|ily|love|romantic
💙	0	青いハート|ハート|青|blue heart|143|blue|emotion|heart|ily|love|romance
🩵	0	薄い青のハート|かわいい|シアン|ティールブルー|ハート|ラブ|好き|愛|水色|空色|薄い青|light blue heart|143|blue|cute|cyan|emotion|heart|ily|light|like|love|sky|special|teal
💜	0	紫のハート|ハート|愛|紫|purple heart|143|bestest|emotion|heart|ily|love|purple
🤎	0	茶色いハート|ハート|茶色|brown heart|143|brown|heart
🖤	0	黒いハート|ハート|悪意|黒|black heart|black|evil|heart|wicked
🩶	0	灰色のハート|グレー|スレートグレー|ハート|ラブ|好き|愛|灰色|銀色|grey heart|143|emotion|gray|grey|heart|ily|love|silver|slate|special
🤍	0	白いハート|ハート|白|white heart|143|heart|white
💋	0	キスマーク|キス|セクシー|ちゅっ|唇|kiss mark|dating|emotion|heart|kiss|kissing|lips|mark|romance|sexy
💯	0	100点満点|100|100点|全問正解|満点|hundred points|a+|agree|clearly|definitely|faithful|fleek|full|hundred|keep|perfect|point|score|true|truth|yup
💢	0	むかっ|＃|マンガ|怒った|怒り|怒りマーク|anger symbol|anger|angry|comic|mad|symbol|upset
💥	0	衝突|どんっ|ぼんっ|マンガ|爆発|衝撃|collision|bomb|boom|collide|comic|explode
💫	0	くらくら|きらきら|キラキラ|星|流れ星|目が回る|dizzy|comic|shining|shooting|star|stars
💦	0	あせあせ|しずく|水滴|汗|sweat droplets|comic|drip|droplet|droplets|drops|splashing|squirt|sweat|water|wet|work|workout
💨	0	ダッシュ|マンガ|急ぐ|急げ|走る|逃げる|dashing away|away|cloud|comic|dash|dashing|fart|fast|go|gone|gotta|running|smoke
🕳️	0	穴|hole
💬	0	会話の吹き出し|セリフ|トーク|マンガ|メッセージ|会話|吹き出し|speech balloon|balloon|bubble|comic|dialog|message|sms|speech|talk|text|typing
👁️‍🗨️	0	吹き出しの目|吹き出し|目|目撃|eye in speech bubble|balloon|bubble|eye|speech|witness
🗨️	0	吹き出し左|セリフ|会話|吹き出し|left speech bubble|balloon|bubble|dialog|left|speech
🗯️	0	怒りの吹き出し|吹き出し|怒り|right anger bubble|anger|angry|balloon|bubble|mad|right
💭	0	雲形の吹き出し|マンガ|もくもく|吹き出し|空想|考え事|雲|thought balloon|balloon|bubble|cartoon|cloud|comic|daydream|decisions|dream|idea|invent|invention|realize|think|thoughts|wonder
💤	0	グーグー|zzz|いびき|おやすみ|マンガ|寝る|就寝中|comic|good|goodnight|night|sleep|sleeping|sleepy|tired
👋	1	手を振る|あいさつ|さようなら|バイバイ|ハロー|またね|手|waving hand|bye|cya|g2g|greetings|gtg|hand|hello|hey|hi|later|outtie|ttfn|ttyl|wave|yo|you
🤚	1	手の甲|手|raised back of hand|back|backhand|hand|raised
🖐️	1	開いた手|ストップ|パー|手|挙手|hand with fingers splayed|finger|fingers|hand|raised|splayed|stop
✋	1	挙手|5|ストップ|パー|手|raised hand|five|hand|high|raised|stop
🖖	1	バルカンの挨拶|バルカンサイン|バルカン人|手|vulcan salute|finger|hand|hands|salute|vulcan
🫱	1	右に向けた手|右側|手|握手|方向|rightwards hand|hand|handshake|hold|reach|right|rightward|rightwards|shake
🫲	1	左に向けた手|左側|手|握手|方向|leftwards hand|hand|handshake|hold|left|leftward|leftwards|reach|shake
🫳	1	下に向けた手|下|手|拾う|落とす|palm down hand|dismiss|down|drop|dropped|hand|palm|pick|shoo|up
🫴	1	上に向けた手|おいで|ちょうだい|上|受ける|差し出す|手|palm up hand|beckon|catch|come|hand|hold|know|lift|me|offer|palm|tell
🫷	1	左向きに押している手|ストップ|ちょっと待って|ハイタッチ|左向き|待った|待て|手|押す|断る|止まれ|leftwards pushing hand|block|five|halt|hand|high|hold|leftward|leftwards|pause|push|pushing|refuse|slap|stop|wait
🫸	1	右向きに押している手|ストップ|ちょっと待って|ハイタッチ|右向き|待った|待て|手|押す|断る|止まれ|rightwards pushing hand|block|five|halt|hand|high|hold|pause|push|pushing|refuse|rightward|rightwards|slap|stop|wait
👌	1	okの手|ok|オーケー|オッケー|つまむ|手|ok hand|awesome|bet|dope|fleek|fosho|got|gotcha|hand|legit|okay|pinch|rad|sure|sweet|three
🤌	1	上向きにすぼめた手|ジェスチャー|すぼめる|手|指|pinched fingers|fingers|gesture|hand|hold|huh|interrogation|patience|pinched|relax|sarcastic|ugh|what|zip
🤏	1	つまんでいる指|ちょっと|少しだけ|手|指|pinching hand|amount|bit|fingers|hand|little|pinching|small|sort
✌️	1	vサイン|チョキ|ピース|手|指|victory hand|hand|peace|v|victory
🤞	1	指をクロス|幸運を|手|指|crossed fingers|cross|crossed|finger|fingers|hand|luck
🫰	1	親指と人差し指をクロス|お金|愛|手|指|指ハート|指をクロス|hand with index finger and thumb crossed|<3|crossed|expensive|finger|hand|heart|index|love|money|snap|thumb
🤟	1	アイラブユー|愛してる|手|love-you gesture|fingers|gesture|hand|ily|love|love-you|three|you
🤘	1	角の指サイン|キツネ|ロックオン|人差し指と小指を立てた手|手|sign of the horns|finger|hand|horns|rock-on|sign
🤙	1	電話の合図|手|電話|電話のジェスチャー|call me hand|call|hand|hang|loose|me|shaka
👈	1	左指差し|左|手|指|指差し|backhand index pointing left|backhand|finger|hand|index|left|point|pointing
👉	1	右指差し|右|手|指|指差し|backhand index pointing right|backhand|finger|hand|index|point|pointing|right
👆	1	手の甲上指差し|上|手|指|指差し|backhand index pointing up|backhand|finger|hand|index|point|pointing|up
🖕	1	立てた中指|上|中指|手|指|指差し|middle finger|finger|hand|middle
👇	1	下指差し|下|手|指|指差し|backhand index pointing down|backhand|down|finger|hand|index|point|pointing
☝️	1	上指差し|上|手|指差し|index pointing up|finger|hand|index|point|pointing|this|up
🫵	1	人を指差している手|おまえだ|手|指|指差し|指摘|index pointing at the viewer|at|finger|hand|index|pointing|poke|viewer|you
👍	1	サムズアップ|ok|いいね|オーケー|オッケー|グッド|上|了解|手|親指|thumbs up|+1|good|hand|like|thumb|up|yes
👎	1	サムズダウン|ng|だめ|ブーイング|ボツ|下|手|thumbs down|-1|bad|dislike|down|good|hand|no|nope|thumb|thumbs
✊	1	げんこつ|グー|こぶし|手|挙げたこぶし|raised fist|clenched|fist|hand|punch|raised|solidarity
👊	1	グー|げんこつ|こぶし|パンチ|手|oncoming fist|absolutely|agree|boom|bro|bruh|bump|clenched|correct|fist|hand|knuckle|oncoming|pound|punch|rock|ttyl
🤛	1	左向きのこぶし|こぶし|パンチ|左|手|left-facing fist|fist|left-facing|leftwards
🤜	1	右向きのこぶし|こぶし|パンチ|右|手|right-facing fist|fist|right-facing|rightwards
👏	1	拍手|おめでとう|パチパチ|手|手を叩く|clapping hands|applause|approval|awesome|clap|congrats|congratulations|excited|good|great|hand|homie|job|nice|prayed|well|yay
🙌	1	バンザイ|ジェスチャー|両手|手|手を上げる|raising hands|celebration|gesture|hand|hands|hooray|praise|raised|raising
🫶	1	ハート形の手|ハート|ハートポーズ|ラブ|両手ハート|愛|手|heart hands|<3|hands|heart|love|you
👐	1	両手のひら|おっはー|手|手のひら|開いた両手|open hands|hand|hands|hug|jazz|open|swerve
🤲	1	手のひらを揃えた両手|両手|包む手|手|祈り|palms up together|cupped|dua|hands|palms|pray|prayer|together|up|wish
🤝	1	握手|よろしく|合意|契約成立|手|handshake|agreement|deal|hand|meeting|shake
🙏	1	祈り|ありがとう|お願い|ごめんなさい|ジェスチャー|ハイタッチ|合掌|手|folded hands|appreciate|ask|beg|blessed|bow|cmon|five|folded|gesture|hand|high|please|pray|thanks|thx
✍️	1	書いている手|手|手で書く|手書き|writing hand|hand|write|writing
💅	1	マニキュアを塗る手|ネイル|マニキュア|爪|nail polish|bored|care|cosmetics|done|makeup|manicure|nail|polish|whatever
🤳	1	セルフィー|カメラ|スマホ|自分撮り|自撮り|selfie|camera|phone
💪	1	力こぶ|ボディビル|ムキムキ|筋トレ|筋肉|腕|flexed biceps|arm|beast|bench|biceps|bodybuilder|bro|curls|flex|gains|gym|jacked|muscle|press|ripped|strong|weightlift
🦾	1	義手|アクセシビリティ|人工装具|手|腕|mechanical arm|accessibility|arm|mechanical|prosthetic
🦿	1	義足|アクセシビリティ|人工装具|脚|足|mechanical leg|accessibility|leg|mechanical|prosthetic
🦵	1	脚|キック|曲げた脚|膝|足|leg|bent|foot|kick|knee|limb
🦶	1	足|キック|くるぶし|ダンス|足首|踏みつける|foot|ankle|feet|kick|stomp
👂	1	耳|からだ|聞き耳|聞く|ear|body|ears|hear|hearing|listen|listening|sound
🦻	1	補聴器を付けた耳|アクセシビリティ|耳|聴覚|難聴|ear with hearing aid|accessibility|aid|ear|hard|hearing
👃	1	鼻|からだ|nose|body|noses|nosey|odor|smell|smells
🧠	1	脳|知識|脳みそ|頭|brain|intelligent|smart
🫀	1	心臓|心拍|脈|臓器|鼓動|anatomical heart|anatomical|beat|cardiology|heart|heartbeat|organ|pulse|real|red
🫁	1	肺|呼吸|臓器|lungs|breath|breathe|exhalation|inhalation|lung|organ|respiration
🦷	1	歯|歯医者|白い歯|tooth|dentist|pearly|teeth|white
🦴	1	骨|犬|骨格|bone|bones|dog|skeleton|wishbone
👀	1	目|からだ|両目|動く目玉|eyes|body|eye|face|googly|look|looking|omg|peep|see|seeing
👁️	1	片目|からだ|一つ目|目|eye|1|body|one
👅	1	舌|からだ|べー|べろ|tongue|body|lick|slurp
👄	1	口|からだ|キス|唇|mouth|beauty|body|kiss|kissing|lips|lipstick
🫦	1	唇を咬んでいる口|セクシー|チッ|口|口惜しさ|唇|悔しい|誘惑|biting lip|anxious|bite|biting|fear|flirt|flirting|kiss|lip|lipstick|nervous|sexy|uncomfortable|worried|worry
👶	1	赤ん坊|ベビー|子供|新生児|赤ちゃん|顔|baby|babies|children|goo|infant|newborn|pregnant|young
🧒	1	子供|キッズ|中性的|子ども|顔|child|bright-eyed|grandchild|kid|young|younger
👦	1	男の子|子供|少年|顔|boy|bright-eyed|child|grandson|kid|son|young|younger
👧	1	女の子|おさげ|女|娘|子供|少女|顔|girl|bright-eyed|child|daughter|granddaughter|kid|virgo|young|younger|zodiac
🧑	1	大人|中性的|成人|顔|person|adult
👱	1	金髪の人|ブロンド|人|人間|金髪|顔|person: blond hair|blond|blond-haired|human|person
👨	1	男性|大人|男|顔|man|adult|bro
🧔	1	あごひげの人|あごひげ|ひげ|人|顔|person: beard|beard|bearded|person|whiskers
🧔‍♂️	1	あごひげの男性|あごひげ|ひげ|男|顔|man: beard|beard|bearded|man|whiskers
🧔‍♀️	1	あごひげの女性|あごひげ|ひげ|女|顔|woman: beard|beard|bearded|whiskers|woman
👨‍🦰	1	男性: 赤毛|大人|男|男性|赤毛|顔|man: red hair|adult|bro|man|red hair
👨‍🦱	1	男性: 巻き毛|大人|巻き毛|男|男性|顔|man: curly hair|adult|bro|curly hair|man
👨‍🦳	1	男性: 白髪|大人|男|男性|白髪|顔|man: white hair|adult|bro|man|white hair
👨‍🦲	1	男性: はげ頭|はげ頭|大人|男|男性|顔|man: bald|adult|bald|bro|man
👩	1	女性|大人|女|顔|woman|adult|lady
👩‍🦰	1	女性: 赤毛|大人|女|女性|赤毛|顔|woman: red hair|adult|lady|red hair|woman
🧑‍🦰	1	大人: 赤毛|中性的|大人|成人|赤毛|顔|person: red hair|adult|person|red hair
👩‍🦱	1	女性: 巻き毛|大人|女|女性|巻き毛|顔|woman: curly hair|adult|curly hair|lady|woman
🧑‍🦱	1	大人: 巻き毛|中性的|大人|巻き毛|成人|顔|person: curly hair|adult|curly hair|person
👩‍🦳	1	女性: 白髪|大人|女|女性|白髪|顔|woman: white hair|adult|lady|white hair|woman
🧑‍🦳	1	大人: 白髪|中性的|大人|成人|白髪|顔|person: white hair|adult|person|white hair
👩‍🦲	1	女性: はげ頭|はげ頭|大人|女|女性|顔|woman: bald|adult|bald|lady|woman
🧑‍🦲	1	大人: はげ頭|はげ頭|中性的|大人|成人|顔|person: bald|adult|bald|person
👱‍♀️	1	金髪の女性|ブロンド|女|女性|金髪|woman: blond hair|blond|blond-haired|blonde|hair|woman
👱‍♂️	1	金髪の男性|ブロンド|男|男性|金髪|man: blond hair|blond|blond-haired|hair|man
🧓	1	お年寄り|中性的|大人|老人|顔|高齢者|older person|adult|elderly|grandparent|old|person|wise
👴	1	おじいさん|おじいちゃん|お年寄り|男性|老人|薄毛|顔|高齢者|old man|adult|bald|elderly|gramps|grandfather|grandpa|man|old|wise
👵	1	おばあさん|おばあちゃん|お年寄り|女性|老人|顔|高齢者|old woman|adult|elderly|grandma|grandmother|granny|lady|old|wise|woman
🙍	1	しかめ面の人|しかめっ面|しかめ顔|むっとしている人|不満|失望|person frowning|annoyed|disappointed|disgruntled|disturbed|frown|frowning|frustrated|gesture|irritated|person|upset
🙍‍♂️	1	しかめ面の男|しかめっ面|しかめ顔|むっとしている男|男|男性|man frowning|annoyed|disappointed|disgruntled|disturbed|frown|frowning|frustrated|gesture|irritated|man|upset
🙍‍♀️	1	しかめ面の女|しかめっ面|しかめ顔|女|女性|woman frowning|annoyed|disappointed|disgruntled|disturbed|frown|frowning|frustrated|gesture|irritated|upset|woman
🙎	1	不機嫌な人|ふくれる|ぷんぷん|不機嫌|口を尖らせた人|険しい表情|person pouting|disappointed|downtrodden|frown|grimace|person|pouting|scowl|sulk|upset|whine
🙎‍♂️	1	不機嫌な男|ぷんぷん|不機嫌|口を尖らせた男|男|男性|man pouting|disappointed|downtrodden|frown|grimace|man|pouting|scowl|sulk|upset|whine
🙎‍♀️	1	不機嫌な女|ぷんぷん|不機嫌|女|女性|woman pouting|disappointed|downtrodden|frown|grimace|pouting|scowl|sulk|upset|whine|woman
🙅	1	ダメのポーズをする人|ng|お断り|ジェスチャー|だめ|バツ|人|禁止|禁止された|除外|person gesturing no|forbidden|gesture|hand|no|not|person|prohibit
🙅‍♂️	1	ダメのポーズをする男|ng|ジェスチャー|バツ|男|男性|禁止|man gesturing no|forbidden|gesture|hand|man|no|not|prohibit
🙅‍♀️	1	ダメのポーズをする女|ng|ジェスチャー|バツ|女|女性|woman gesturing no|forbidden|gesture|hand|no|not|prohibit|woman
🙆	1	okのポーズをする人|ok|エクササイズ|オーケー|ジェスチャー|びっくり|丸|人|手|person gesturing ok|exercise|gesture|gesturing|hand|omg|person
🙆‍♂️	1	okのポーズをする男|ok|オーケー|オッケー|ジェスチャー|丸|男|男性|man gesturing ok|exercise|gesture|gesturing|hand|man|omg
🙆‍♀️	1	okのポーズをする女|オーケー|オッケー|ジェスチャー|丸|女性|woman gesturing ok|exercise|gesture|gesturing|hand|ok|omg|woman
💁	1	案内する人|ゴシップ|ジェスチャー|受付|受付嬢|手を返している人|案内|案内する女|髪をかき上げる|髪を振り払う|person tipping hand|fetch|flick|flip|gossip|hand|person|sarcasm|sarcastic|sassy|seriously|tipping|whatever
💁‍♂️	1	案内する男|ジェスチャー|案内|男|男性|man tipping hand|fetch|flick|flip|gossip|hand|man|sarcasm|sarcastic|sassy|seriously|tipping|whatever
💁‍♀️	1	案内する女|ジェスチャー|女|女性|案内|woman tipping hand|fetch|flick|flip|gossip|hand|sarcasm|sarcastic|sassy|seriously|tipping|whatever|woman
🙋	1	手を挙げる人|ジェスチャー|人|分かった|手|手を挙げる|挙手|質問|person raising hand|gesture|hand|here|know|me|person|pick|question|raise|raising
🙋‍♂️	1	手を挙げる男|手を挙げる|挙手|男|男性|man raising hand|gesture|hand|here|know|man|me|pick|question|raise|raising
🙋‍♀️	1	手を挙げる女|女|女性|手を挙げる|挙手|woman raising hand|gesture|hand|here|know|me|pick|question|raise|raising|woman
🧏	1	耳の不自由な人|アクセシビリティ|人|聞こえない|聴覚|障がい|deaf person|accessibility|deaf|ear|gesture|hear|person
🧏‍♂️	1	耳の不自由な男性|アクセシビリティ|男性|聴覚|障がい|deaf man|accessibility|deaf|ear|gesture|hear|man
🧏‍♀️	1	耳の不自由な女性|アクセシビリティ|女性|聴覚|障がい|deaf woman|accessibility|deaf|ear|gesture|hear|woman
🙇	1	おじぎする人|おじぎ|お願い|ごめんなさい|ジェスチャー|土下座|許して|謝罪|person bowing|apology|ask|beg|bow|bowing|favor|forgive|gesture|meditate|meditation|person|pity|regret|sorry
🙇‍♂️	1	おじぎする男|おじぎ|ごめんなさい|土下座|男|男性|man bowing|apology|ask|beg|bow|bowing|favor|forgive|gesture|man|meditate|meditation|pity|regret|sorry
🙇‍♀️	1	おじぎする女|おじぎ|ごめんなさい|土下座|女|女性|woman bowing|apology|ask|beg|bow|bowing|favor|forgive|gesture|meditate|meditation|pity|regret|sorry|woman
🤦	1	ひたいに手をあてる人|あちゃー|うそでしょ|ジェスチャー|ショック|ひたいに手|またか|人|信じられない|person facepalming|again|bewilder|disbelief|exasperation|facepalm|no|not|oh|omg|person|shock|smh
🤦‍♂️	1	ひたいに手をあてる男|あちゃー|うそでしょ|ジェスチャー|ショック|ひたいに手|またか|信じられない|男|男性|man facepalming|again|bewilder|disbelief|exasperation|facepalm|man|no|not|oh|omg|shock|smh
🤦‍♀️	1	ひたいに手をあてる女|あちゃー|うそでしょ|ジェスチャー|ショック|ひたいに手|またか|信じられない|女|女性|woman facepalming|again|bewilder|disbelief|exasperation|facepalm|no|not|oh|omg|shock|smh|woman
🤷	1	お手上げする人|お手上げ|さあね|ジェスチャー|わかりません|人|多分ね|知らない|肩をすくめる|person shrugging|doubt|dunno|guess|idk|ignorance|indifference|knows|maybe|person|shrug|shrugging|whatever|who
🤷‍♂️	1	お手上げする男|お手上げ|さあね|ジェスチャー|わかりません|多分ね|男|男性|知らない|肩をすくめる|man shrugging|doubt|dunno|guess|idk|ignorance|indifference|knows|man|maybe|shrug|shrugging|whatever|who
🤷‍♀️	1	お手上げする女|お手上げ|さあね|ジェスチャー|わかりません|多分ね|女|女性|知らない|肩をすくめる|woman shrugging|doubt|dunno|guess|idk|ignorance|indifference|knows|maybe|shrug|shrugging|whatever|who|woman
🧑‍⚕️	1	医者|セラピスト|医師|health worker|doctor|health|healthcare|nurse|therapist|worker
👨‍⚕️	1	男性の医者|セラピスト|ドクター|医師|医者|男|男性|man health worker|doctor|health|healthcare|man|nurse|therapist|worker
👩‍⚕️	1	女性の医者|セラピスト|ドクター|医師|医者|女|女医|女性|woman health worker|doctor|health|healthcare|nurse|therapist|woman|worker
🧑‍🎓	1	学生|卒業|角帽|student|graduate
👨‍🎓	1	男子学生|卒業|学生|男|男性|角帽|man student|graduate|man|student
👩‍🎓	1	女子学生|卒業|女|女性|学生|角帽|woman student|graduate|student|woman
🧑‍🏫	1	教師|先生|担任|教授|講師|teacher|instructor|lecturer|professor
👨‍🏫	1	男性の教師|先生|教師|教授|男|男性|講師|man teacher|instructor|lecturer|man|professor|teacher
👩‍🏫	1	女性の教師|先生|女|女性|教師|教授|講師|woman teacher|instructor|lecturer|professor|teacher|woman
🧑‍⚖️	1	裁判官|裁判|judge|justice|law|scales
👨‍⚖️	1	男性の裁判官|男|男性|裁判|裁判官|man judge|judge|justice|law|man|scales
👩‍⚖️	1	女性の裁判官|女|女性|裁判|裁判官|woman judge|judge|justice|law|scales|woman
🧑‍🌾	1	農家|ガーデナー|農場主|農業|farmer|gardener|rancher
👨‍🌾	1	農家の男性|男|男性|農夫|農家|man farmer|farmer|gardener|man|rancher
👩‍🌾	1	農家の女性|女|女性|農家|woman farmer|farmer|gardener|rancher|woman
🧑‍🍳	1	コック|シェフ|料理|cook|chef
👨‍🍳	1	男性のコック|コック|シェフ|料理|料理人|男|男性|man cook|chef|cook|man
👩‍🍳	1	女性のコック|コック|シェフ|女|女性|料理|料理人|woman cook|chef|cook|woman
🧑‍🔧	1	整備士|スパナ|メカニック|作業員|工作|mechanic|electrician|plumber|tradesperson
👨‍🔧	1	男性の整備士|スパナ|メカニック|工作|男|男性|配管工|電気工|man mechanic|electrician|man|mechanic|plumber|tradesperson
👩‍🔧	1	女性の整備士|スパナ|メカニック|女|女性|工作|配管工|電気工|woman mechanic|electrician|mechanic|plumber|tradesperson|woman
🧑‍🏭	1	溶接工|モノづくり|工員|工場|factory worker|assembly|factory|industrial|worker
👨‍🏭	1	男性の溶接工|工員|溶接|男|男性|man factory worker|assembly|factory|industrial|man|worker
👩‍🏭	1	女性の溶接工|女|女性|工員|溶接|woman factory worker|assembly|factory|industrial|woman|worker
🧑‍💼	1	会社員|サラリーマン|ビジネスパーソン|office worker|architect|business|manager|office|white-collar|worker
👨‍💼	1	男性会社員|サラリーマン|ビジネスパーソン|ビジネスマン|会社員|男性|man office worker|architect|business|man|manager|office|white-collar|worker
👩‍💼	1	女性会社員|ol|サラリーマン|ビジネスパーソン|会社員|女|女性|woman office worker|architect|business|manager|office|white-collar|woman|worker
🧑‍🔬	1	科学者|化学者|物理学者|生物学者|scientist|biologist|chemist|engineer|mathematician|physicist
👨‍🔬	1	男性科学者|男|男性|科学者|man scientist|biologist|chemist|engineer|man|mathematician|physicist|scientist
👩‍🔬	1	女性科学者|女|女性|科学者|woman scientist|biologist|chemist|engineer|mathematician|physicist|scientist|woman
🧑‍💻	1	技術者|コンピュータ|パソコン|プログラマ|開発者|technologist|coder|computer|developer|inventor|software
👨‍💻	1	男性技術者|コンピュータ|デベロッパ|パソコン|プログラマ|プログラマー|男|男性|開発者|man technologist|coder|computer|developer|inventor|man|software|technologist
👩‍💻	1	女性技術者|コンピュータ|デベロッパ|パソコン|プログラマ|プログラマー|女|女性|開発者|woman technologist|coder|computer|developer|inventor|software|technologist|woman
🧑‍🎤	1	歌手|アーティスト|シンガー|スター|ロッカー|singer|actor|entertainer|rock|rockstar|star
👨‍🎤	1	男性歌手|アーティスト|エンターテイナー|シンガー|スター|ロックスター|俳優|歌手|男|男性|man singer|actor|entertainer|man|rock|rockstar|singer|star
👩‍🎤	1	女性歌手|アーティスト|エンターテイナー|シンガー|スター|ロックスター|俳優|女|女性|歌手|woman singer|actor|entertainer|rock|rockstar|singer|star|woman
🧑‍🎨	1	芸術家|アーティスト|画家|絵描き|artist|palette
👨‍🎨	1	男性の芸術家|アーティスト|男|男性|画家|芸術家|man artist|artist|man|palette
👩‍🎨	1	女性の芸術家|アーティスト|パレット|女|女性|画家|芸術家|woman artist|artist|palette|woman
🧑‍✈️	1	パイロット|操縦士|機長|pilot|plane
👨‍✈️	1	男性パイロット|パイロット|操縦士|男|男性|man pilot|man|pilot|plane
👩‍✈️	1	女性パイロット|女|女性|操縦士|woman pilot|pilot|plane|woman
🧑‍🚀	1	宇宙飛行士|ロケット|astronaut|rocket|space
👨‍🚀	1	男性宇宙飛行士|宇宙飛行士|男|男性|man astronaut|astronaut|man|rocket|space
👩‍🚀	1	女性宇宙飛行士|女|女性|宇宙飛行士|woman astronaut|astronaut|rocket|space|woman
🧑‍🚒	1	消防士|レスキュー|救助隊|firefighter|fire|firetruck
👨‍🚒	1	男性消防士|消防士|男|男性|man firefighter|fire|firefighter|firetruck|man
👩‍🚒	1	女性消防士|女|女性|消防士|woman firefighter|fire|firefighter|firetruck|woman
👮	1	警察官|お巡りさん|警官|警察|逮捕する|police officer|apprehend|arrest|citation|cop|law|officer|over|police|pulled|undercover
👮‍♂️	1	男性警察官|お巡りさん|男性|警官|警察|警察官|man police officer|apprehend|arrest|citation|cop|law|man|officer|over|police|pulled|undercover
👮‍♀️	1	女性警察官|お巡りさん|女性|警官|警察|警察官|woman police officer|apprehend|arrest|citation|cop|law|officer|over|police|pulled|undercover|woman
🕵️	1	探偵|スパイ|私立探偵|虫めがね|detective|sleuth|spy
🕵️‍♂️	1	男性の探偵|探偵|男|男性|虫めがね|man detective|detective|man|sleuth|spy
🕵️‍♀️	1	女性の探偵|女|女性|探偵|私立探偵|虫めがね|woman detective|detective|sleuth|spy|woman
💂	1	衛兵|守衛|警備|門番|guard|buckingham|helmet|london|palace
💂‍♂️	1	男性の衛兵|守衛|男性|衛兵|警備|門番|man guard|buckingham|guard|helmet|london|man|palace
💂‍♀️	1	女性の衛兵|女性|守衛|衛兵|警備|門番|woman guard|buckingham|guard|helmet|london|palace|woman
🥷	1	忍者|スパイ|人|忍び|忍術|草の者|隠密|ninja|assassin|fight|fighter|hidden|person|secret|skills|sly|soldier|stealth|war
👷	1	建設作業員|ハンマー|ヘルメット|作業員|工事|建設|construction worker|build|construction|fix|hardhat|hat|man|person|rebuild|remodel|repair|work|worker
👷‍♂️	1	男性の建設作業員|ハンマー|ヘルメット|作業員|工事|建設|男|男性|man construction worker|build|construction|fix|hardhat|hat|man|rebuild|remodel|repair|work|worker
👷‍♀️	1	女性の建設作業員|ハンマー|ヘルメット|作業員|女|女性|工事|建設|woman construction worker|build|construction|fix|hardhat|hat|man|rebuild|remodel|repair|woman|work|worker
🫅	1	王冠をかぶった人|君主|国王|女王|王|王家|王族|王様|person with crown|crown|monarch|noble|person|regal|royal|royalty
🤴	1	プリンス|王子|王族|男|男性|prince|crown|fairy|fairytale|fantasy|king|royal|royalty|tale
👸	1	プリンセス|お姫さま|クイーン|冠|女|女性|女王|王女|王妃|princess|crown|fairy|fairytale|fantasy|queen|royal|royalty|tale
👳	1	ターバンの人|ターバン|帽子|person wearing turban|person|turban|wearing
👳‍♂️	1	ターバンの男性|ターバン|男|男性|man wearing turban|man|turban|wearing
👳‍♀️	1	ターバンの女性|ターバン|女|女性|woman wearing turban|turban|wearing|woman
👲	1	中華帽の男性|おわん帽|男|男性|person with skullcap|cap|chinese|gua|guapi|hat|mao|person|pi|skullcap
🧕	1	スカーフの女性|スカーフ|ヒジャブ|ベール|女|女性|woman with headscarf|bandana|head|headscarf|hijab|kerchief|mantilla|tichel|woman
🤵	1	タキシードの人|タキシード|フォーマル|人|正装|結婚|person in tuxedo|formal|person|tuxedo|wedding
🤵‍♂️	1	タキシードの男性|タキシード|タキシードの男|男|男性|man in tuxedo|formal|groom|man|tuxedo|wedding
"""

private const val UNICODE_EMOJI_DATA_1: String = """
🤵‍♀️	1	タキシードの女性|タキシード|タキシードの女|女|女性|woman in tuxedo|formal|tuxedo|wedding|woman
👰	1	ベールの人|ウェディング|ベール|人|結婚|顔|person with veil|person|veil|wedding
👰‍♂️	1	ベールの男性|ベール|男|男性|man with veil|man|veil|wedding
👰‍♀️	1	ベールの女性|ベール|女|女性|woman with veil|bride|veil|wedding|woman
🤰	1	妊婦|女|女性|妊娠|pregnant woman|pregnant|woman
🫃	1	妊夫|妊娠|妊娠した人|男|男性|食べ過ぎ|pregnant man|belly|bloated|full|man|overeat|pregnant
🫄	1	妊娠した人|お腹|妊夫|妊娠|妊婦|食べ過ぎ|pregnant person|belly|bloated|full|overeat|person|pregnant|stuffed
🤱	1	授乳|ミルク|女|女性|赤ちゃん|breast-feeding|baby|breast|feeding|mom|mother|nursing|woman
👩‍🍼	1	授乳する女性|ママ|人|女|女性|授乳|新生児|母親|赤ちゃん|woman feeding baby|baby|feed|feeding|mom|mother|nanny|newborn|nursing|woman
👨‍🍼	1	授乳する男性|パパ|人|授乳|新生児|父親|男|男性|赤ちゃん|man feeding baby|baby|dad|father|feed|feeding|man|nanny|newborn|nursing
🧑‍🍼	1	授乳する人|人|授乳|新生児|母親|父親|赤ちゃん|person feeding baby|baby|feed|feeding|nanny|newborn|nursing|parent
👼	1	天使|エンジェル|エンゼル|赤ちゃん天使|顔|baby angel|angel|baby|church|face|fairy|fairytale|fantasy|tale
🎅	1	サンタ|クリスマス|サンタクロース|顔|santa claus|celebration|christmas|claus|fairy|fantasy|father|holiday|merry|santa|tale|xmas
🤶	1	女性のサンタ|クリスマス|サンタクロース|女|女性|mrs. claus|celebration|christmas|claus|fairy|fantasy|holiday|merry|mother|mrs|santa|tale|xmas
🧑‍🎄	1	サンタさん|クリスマス|サンタ|サンタクロース|人|帽子|mx claus|celebration|christmas|claus|fairy|fantasy|holiday|merry|mx|santa|tale|xmas
🦸	1	スーパーヒーロー|スーパーマン|ヒーロー|ヒロイン|善|巨大勢力|超人|superhero|good|hero|superpower
🦸‍♂️	1	男性のスーパーヒーロー|ヒーロー|善|男性|超人|man superhero|good|hero|man|superhero|superpower
🦸‍♀️	1	女性のスーパーヒーロー|ヒーロー|ヒロイン|善|女性|超人|woman superhero|good|hero|heroine|superhero|superpower|woman
🦹	1	悪役|ヴィラン|人|悪|悪い人|悪人|悪玉|犯人|犯罪者|超悪玉|supervillain|bad|criminal|evil|superpower|villain
🦹‍♂️	1	男性の悪役|ヴィラン|悪|犯人|男性|超人|man supervillain|bad|criminal|evil|man|superpower|supervillain|villain
🦹‍♀️	1	女性の悪役|ヴィラン|女性|悪|犯人|超人|woman supervillain|bad|criminal|evil|superpower|supervillain|villain|woman
🧙	1	魔法使い|ファンタジー|ロールプレイ|召喚|呪文|魔女|魔法|魔術師|mage|fantasy|magic|play|sorcerer|sorceress|sorcery|spell|summon|witch|wizard
🧙‍♂️	1	男の魔法使い|男|男性|魔法使い|man mage|fantasy|mage|magic|man|play|sorcerer|sorceress|sorcery|spell|summon|witch|wizard
🧙‍♀️	1	女の魔法使い|女|女性|魔女|魔法使い|woman mage|fantasy|mage|magic|play|sorcerer|sorceress|sorcery|spell|summon|witch|wizard|woman
🧚	1	妖精|おとぎ話|オベロン|パック|ファンタジー|伝説|神話|童話|魔法|fairy|fairytale|fantasy|myth|person|pixie|tale|wings
🧚‍♂️	1	男の妖精|妖精|男|男性|man fairy|fairy|fairytale|fantasy|man|myth|oberon|person|pixie|puck|tale|wings
🧚‍♀️	1	女の妖精|女|女性|妖精|woman fairy|fairy|fairytale|fantasy|myth|person|pixie|tale|titania|wings|woman
🧛	1	吸血鬼|ドラキュラ|ハロウィーン|バンパイア|不死身|恐怖|歯|牙|血|vampire|blood|dracula|fangs|halloween|scary|supernatural|teeth|undead
🧛‍♂️	1	男の吸血鬼|ドラキュラ|バンパイア|吸血鬼|男|男性|man vampire|blood|fangs|halloween|man|scary|supernatural|teeth|undead|vampire
🧛‍♀️	1	女の吸血鬼|ドラキュラ|バンパイア|吸血鬼|女|女性|woman vampire|blood|fangs|halloween|scary|supernatural|teeth|undead|vampire|woman
🧜	1	人魚|おとぎ話|サイレン|マーメイド|伝説|半魚人|海の生物|海底|merperson|creature|fairytale|folklore|ocean|sea|siren|trident
🧜‍♂️	1	マーマン|人魚|男|男性|merman|creature|fairytale|folklore|neptune|ocean|poseidon|sea|siren|trident|triton
🧜‍♀️	1	マーメイド|人魚|女|女性|mermaid|creature|fairytale|folklore|merwoman|ocean|sea|siren|trident
🧝	1	エルフ|ファンタジー|伝説|妖精|神秘的|神話|魔力|魔法|elf|elves|enchantment|fantasy|folklore|magic|magical|myth
🧝‍♂️	1	男のエルフ|エルフ|男|男性|man elf|elf|elves|enchantment|fantasy|folklore|magic|magical|man|myth
🧝‍♀️	1	女のエルフ|エルフ|女|女性|woman elf|elf|elves|enchantment|fantasy|folklore|magic|magical|myth|woman
🧞	1	精霊|ジニー|ファンタジー|ランプをこする|不可思議|神話|魔人|genie|djinn|fantasy|jinn|lamp|myth|rub|wishes
🧞‍♂️	1	男の精霊|男|男性|精霊|魔神|man genie|djinn|fantasy|genie|jinn|lamp|man|myth|rub|wishes
🧞‍♀️	1	女の精霊|女|女性|精霊|魔神|woman genie|djinn|fantasy|genie|jinn|lamp|myth|rub|wishes|woman
🧟	1	ゾンビ|アンデッド|ハロウィーン|ホラー|大災害|恐怖|歩く屍|zombie|apocalypse|dead|halloween|horror|scary|undead|walking
🧟‍♂️	1	男のゾンビ|ゾンビ|ホラー|男|男性|man zombie|apocalypse|dead|halloween|horror|man|scary|undead|walking|zombie
🧟‍♀️	1	女のゾンビ|ゾンビ|ホラー|女|女性|woman zombie|apocalypse|dead|halloween|horror|scary|undead|walking|woman|zombie
🧌	1	トロール|おとぎ話|ファンタジー|モンスター|troll|fairy|fantasy|monster|tale|trolling
💆	1	フェイスマッサージ中の人|エステ|サロン|スパ|セラピー|フェイスマッサージ|マッサージ|リラックス|施術|癒し|person getting massage|face|getting|headache|massage|person|relax|relaxing|salon|soothe|spa|tension|therapy|treatment
💆‍♂️	1	フェイスマッサージ中の男|エステ|フェイスマッサージ|マッサージ|リラックス|男|男性|顔|man getting massage|face|getting|headache|man|massage|relax|relaxing|salon|soothe|spa|tension|therapy|treatment
💆‍♀️	1	フェイスマッサージ中の女|エステ|フェイスマッサージ|マッサージ|女|女性|woman getting massage|face|getting|headache|massage|relax|relaxing|salon|soothe|spa|tension|therapy|treatment|woman
💇	1	散髪される人|カット|ヘアカット|床屋|理容室|理容院|美容室|美容院|髪|person getting haircut|barber|beauty|chop|cosmetology|cut|groom|hair|haircut|parlor|person|shears|style
💇‍♂️	1	散髪される男|カット|ヘアカット|床屋|理容室|理容院|男|男性|美容室|美容院|man getting haircut|barber|beauty|chop|cosmetology|cut|groom|hair|haircut|man|parlor|person|shears|style
💇‍♀️	1	散髪される女|ヘアカット|女性|床屋|美容室|美容院|woman getting haircut|barber|beauty|chop|cosmetology|cut|groom|hair|haircut|parlor|person|shears|style|woman
🚶	1	歩く人|ウォーキング|ハイキング|ぶらつく|人|大股|散歩|歩く|歩行|歩行者|person walking|amble|gait|hike|man|pace|pedestrian|person|stride|stroll|walk|walking
🚶‍♂️	1	歩く男|ウォーキング|歩行|歩行者|男性|man walking|amble|gait|hike|man|pace|pedestrian|stride|stroll|walk|walking
🚶‍♀️	1	歩く女|ウォーキング|ハイキング|女性|散歩|歩く|歩行|歩行者|woman walking|amble|gait|hike|man|pace|pedestrian|stride|stroll|walk|walking|woman
🚶‍➡️	1	歩く人: 右向き|ウォーキング|ハイキング|ぶらつく|人|右向き|大股|散歩|歩く|歩行|歩行者|person walking: facing right|amble|facing|gait|hike|man|pace|pedestrian|person|right|stride|stroll|walk|walking
🚶‍♀️‍➡️	1	歩く女: 右向き|ウォーキング|ハイキング|右向き|女性|散歩|歩く|歩く女|歩行|歩行者|woman walking: facing right|amble|facing|gait|hike|man|pace|pedestrian|right|stride|stroll|walk|walking|woman
🚶‍♂️‍➡️	1	歩く男: 右向き|ウォーキング|右向き|歩く男|歩行|歩行者|男性|man walking: facing right|amble|facing|gait|hike|man|pace|pedestrian|right|stride|stroll|walk|walking
🧍	1	立つ人|人|立っている|起立|person standing|person|stand|standing
🧍‍♂️	1	立つ男|男性|起立|man standing|man|stand|standing
🧍‍♀️	1	立つ女|女性|起立|woman standing|stand|standing|woman
🧎	1	正座する人|ひざまずく|人|座る|person kneeling|kneel|kneeling|knees|person
🧎‍♂️	1	正座する男性|ひざまずく|座る|男性|man kneeling|kneel|kneeling|knees|man
🧎‍♀️	1	正座する女性|ひざまずく|女性|座る|woman kneeling|kneel|kneeling|knees|woman
🧎‍➡️	1	正座する人: 右向き|ひざまずく|人|右向き|座る|正座する人|person kneeling: facing right|facing|kneel|kneeling|knees|person|right
🧎‍♀️‍➡️	1	正座する女性: 右向き|ひざまずく|右向き|女性|座る|正座する女性|woman kneeling: facing right|facing|kneel|kneeling|knees|right|woman
🧎‍♂️‍➡️	1	正座する男性: 右向き|ひざまずく|右向き|座る|正座する男性|男性|man kneeling: facing right|facing|kneel|kneeling|knees|man|right
🧑‍🦯	1	杖をついた人|アクセシビリティ|目|視覚|障がい|person with white cane|accessibility|blind|cane|person|probing|white
🧑‍🦯‍➡️	1	杖をついた人: 右向き|アクセシビリティ|右向き|杖をついた人|目|視覚|障がい|person with white cane: facing right|accessibility|blind|cane|facing|person|probing|right|white
👨‍🦯	1	杖をついた男性|アクセシビリティ|男性|白杖をついた男性|目|視覚|障がい|man with white cane|accessibility|blind|cane|man|probing|white
👨‍🦯‍➡️	1	杖をついた男性: 右向き|アクセシビリティ|右向き|杖をついた男性|男性|白杖をついた男性|目|視覚|障がい|man with white cane: facing right|accessibility|blind|cane|facing|man|probing|right|white
👩‍🦯	1	杖をついた女性|アクセシビリティ|女性|白杖をついた女性|目|視覚|障がい|woman with white cane|accessibility|blind|cane|probing|white|woman
👩‍🦯‍➡️	1	杖をついた女性: 右向き|アクセシビリティ|右向き|女性|杖をついた女性|白杖をついた女性|目|視覚|障がい|woman with white cane: facing right|accessibility|blind|cane|facing|probing|right|white|woman
🧑‍🦼	1	電動車椅子の人|アクセシビリティ|車いす|障がい|person in motorized wheelchair|accessibility|motorized|person|wheelchair
🧑‍🦼‍➡️	1	電動車椅子の人: 右向き|アクセシビリティ|右向き|車いす|障がい|電動車椅子の人|person in motorized wheelchair: facing right|accessibility|facing|motorized|person|right|wheelchair
👨‍🦼	1	電動車椅子の男性|アクセシビリティ|男性|車いす|障がい|man in motorized wheelchair|accessibility|man|motorized|wheelchair
👨‍🦼‍➡️	1	電動車椅子の男性: 右向き|アクセシビリティ|右向き|男性|車いす|障がい|電動車椅子の男性|man in motorized wheelchair: facing right|accessibility|facing|man|motorized|right|wheelchair
👩‍🦼	1	電動車椅子の女性|アクセシビリティ|女性|車いす|障がい|woman in motorized wheelchair|accessibility|motorized|wheelchair|woman
👩‍🦼‍➡️	1	電動車椅子の女性: 右向き|アクセシビリティ|右向き|女性|車いす|障がい|電動車椅子の女性|woman in motorized wheelchair: facing right|accessibility|facing|motorized|right|wheelchair|woman
🧑‍🦽	1	手動式車椅子の人|アクセシビリティ|車いす|障がい|person in manual wheelchair|accessibility|manual|person|wheelchair
🧑‍🦽‍➡️	1	手動式車椅子の人: 右向き|アクセシビリティ|右向き|手動式車椅子の人|車いす|障がい|person in manual wheelchair: facing right|accessibility|facing|manual|person|right|wheelchair
👨‍🦽	1	手動式車椅子の男性|アクセシビリティ|男性|車いす|障がい|man in manual wheelchair|accessibility|man|manual|wheelchair
👨‍🦽‍➡️	1	手動式車椅子の男性: 右向き|アクセシビリティ|右向き|手動式車椅子の男性|男性|車いす|障がい|man in manual wheelchair: facing right|accessibility|facing|man|manual|right|wheelchair
👩‍🦽	1	手動式車椅子の女性|アクセシビリティ|女性|車いす|障がい|woman in manual wheelchair|accessibility|manual|wheelchair|woman
👩‍🦽‍➡️	1	手動式車椅子の女性: 右向き|アクセシビリティ|右向き|女性|手動式車椅子の女性|車いす|障がい|woman in manual wheelchair: facing right|accessibility|facing|manual|right|wheelchair|woman
🏃	1	走る人|ジョギング|マラソン|ランナー|ランニング|急ぐ|走る|駆け足|person running|fast|hurry|marathon|move|person|quick|race|racing|run|rush|speed
🏃‍♂️	1	走る男|ジョギング|マラソン|ランナー|ランニング|男性|man running|fast|hurry|man|marathon|move|quick|race|racing|run|rush|speed
🏃‍♀️	1	走る女|ジョギング|マラソン|ランナー|ランニング|女性|急ぐ|走る|駆け足|woman running|fast|hurry|marathon|move|quick|race|racing|run|rush|speed|woman
🏃‍➡️	1	走る人: 右向き|ジョギング|マラソン|ランナー|ランニング|右向き|急ぐ|走る|走る人|駆け足|person running: facing right|facing|fast|hurry|marathon|move|person|quick|race|racing|right|run|rush|speed
🏃‍♀️‍➡️	1	走る女: 右向き|ジョギング|マラソン|ランナー|ランニング|右向き|女性|急ぐ|走る|走る女|駆け足|woman running: facing right|facing|fast|hurry|marathon|move|quick|race|racing|right|run|rush|speed|woman
🏃‍♂️‍➡️	1	走る男: 右向き|ジョギング|マラソン|ランナー|ランニング|右向き|男性|走る男|man running: facing right|facing|fast|hurry|man|marathon|move|quick|race|racing|right|run|rush|speed
💃	1	踊る女|サルサ|タンゴ|ダンサー|ダンス|フラメンコ|女|女性|踊り子|woman dancing|dance|dancer|dancing|elegant|festive|flair|flamenco|groove|let’s|salsa|tango|woman
🕺	1	踊る男|サルサ|タンゴ|ダンサー|ダンス|フラメンコ|男|男性|man dancing|dance|dancer|dancing|elegant|festive|flair|flamenco|groove|let’s|man|salsa|tango
🕴️	1	浮いてるビジネスマン|スーツ|ビジネスマン|男|男性|空中浮遊|person in suit levitating|business|levitating|person|suit
👯	1	バニー|うさ耳|ずっ友|そっくり|ダンサー|パーティ|ペア|双子|相方|親友|people with bunny ears|bestie|bff|bunny|counterpart|dancer|double|ear|identical|pair|party|partying|people|soulmate|twin|twinsies
👯‍♂️	1	バニーボーイ|うさ耳|ダンサー|パーティー|男|男性|親友|men with bunny ears|bestie|bff|bunny|counterpart|dancer|double|ear|identical|men|pair|party|partying|people|soulmate|twin|twinsies
👯‍♀️	1	バニーガール|うさ耳|ダンサー|パーティー|女|女性|women with bunny ears|bestie|bff|bunny|counterpart|dancer|double|ear|identical|pair|party|partying|people|soulmate|twin|twinsies|women
🧖	1	サウナに入る人|くつろぐ|サウナ|スパ|リラックス|蒸しぶろ|蒸気|person in steamy room|day|luxurious|pamper|person|relax|room|sauna|spa|steam|steambath|unwind
🧖‍♂️	1	サウナに入る男|サウナ|男|男性|man in steamy room|day|luxurious|man|pamper|relax|room|sauna|spa|steam|steambath|unwind
🧖‍♀️	1	サウナに入る女|サウナ|女|女性|woman in steamy room|day|luxurious|pamper|relax|room|sauna|spa|steam|steambath|unwind|woman
🧗	1	山を登る人|ロッククライマー|山|山登り|登る|登山|person climbing|climb|climber|climbing|mountain|person|rock|scale|up
🧗‍♂️	1	山を登る男|山登り|男|男性|登山|man climbing|climb|climber|climbing|man|mountain|rock|scale|up
🧗‍♀️	1	山を登る女|女|女性|山登り|登山|woman climbing|climb|climber|climbing|mountain|rock|scale|up|woman
🤺	1	フェンシングをする人|スポーツ|フェンシング|person fencing|fencer|fencing|person|sword
🏇	1	競馬|ジョッキー|スポーツ|乗馬|競走馬|馬|騎手|horse racing|horse|jockey|racehorse|racing|riding|sport
⛷️	1	スキーヤー|スキー|ストック|スポーツ|skier|ski|snow
🏂	1	スノーボーダー|スノーボード|スノボ|スポーツ|snowboarder|ski|snow|snowboard|sport
🏌️	1	ゴルフをする人|ゴルフ|ゴルファー|スポーツ|打ちっぱなし|球技|person golfing|ball|birdie|caddy|driving|golf|golfing|green|person|pga|putt|range|tee
🏌️‍♂️	1	ゴルフをする男|ゴルフ|ゴルファー|スポーツ|男|男性|man golfing|ball|birdie|caddy|driving|golf|golfing|green|man|pga|putt|range|tee
🏌️‍♀️	1	ゴルフをする女|ゴルフ|ゴルファー|スポーツ|女|女性|球技|woman golfing|ball|birdie|caddy|driving|golf|golfing|green|pga|putt|range|tee|woman
🏄	1	サーフィンする人|サーファー|サーフィン|スポーツ|波|波乗り|海|person surfing|beach|ocean|person|sport|surf|surfer|surfing|swell|waves
🏄‍♂️	1	サーフィンする男|サーファー|スポーツ|波乗り|男|男性|man surfing|beach|man|ocean|sport|surf|surfer|surfing|swell|waves
🏄‍♀️	1	サーフィンする女|サーファー|スポーツ|女|女性|波乗り|woman surfing|beach|ocean|person|sport|surf|surfer|surfing|swell|waves
🚣	1	ボートをこぐ人|オール|カヌー|カヤック|スポーツ|パドル|ボート|手漕ぎボート|漕ぐ|船|person rowing boat|boat|canoe|cruise|fishing|lake|oar|paddle|person|raft|river|row|rowboat|rowing
🚣‍♂️	1	ボートをこぐ男|カヌー|カヤック|スポーツ|男性|船|man rowing boat|boat|canoe|cruise|fishing|lake|man|oar|paddle|raft|river|row|rowboat|rowing
🚣‍♀️	1	ボートをこぐ女|カヌー|カヤック|スポーツ|ボート|女|女性|手漕ぎボート|船|woman rowing boat|boat|canoe|cruise|fishing|lake|oar|paddle|raft|river|row|rowboat|rowing|woman
🏊	1	泳ぐ人|クロール|スイマー|スイミング|スイム|スポーツ|トライアスロン|水泳|自由形|person swimming|freestyle|person|sport|swim|swimmer|swimming|triathlon
🏊‍♂️	1	泳ぐ男|スイマー|スイミング|スイム|スポーツ|水泳|男性|man swimming|freestyle|man|sport|swim|swimmer|swimming|triathlon
🏊‍♀️	1	泳ぐ女|スイマー|スイミング|スイム|スポーツ|女|女性|水泳|woman swimming|freestyle|man|sport|swim|swimmer|swimming|triathlon
⛹️	1	バスケットボールをする人|スポーツ|ドリブル|バスケ|バスケットボール|バスケットボール選手|球技|person bouncing ball|athletic|ball|basketball|bouncing|championship|dribble|net|person|player|throw
⛹️‍♂️	1	バスケットボールをする男|スポーツ|バスケ|バスケットボール|球技|男性|man bouncing ball|athletic|ball|basketball|bouncing|championship|dribble|man|net|player|throw
⛹️‍♀️	1	バスケットボールをする女|スポーツ|ドリブル|バスケ|バスケットボール|女性|球技|woman bouncing ball|athletic|ball|basketball|bouncing|championship|dribble|net|player|throw|woman
🏋️	1	重量挙げをする人|ウェイトリフティング|スポーツ|デッドリフト|バーベル|パワーリフティング|ボディビルダー|筋トレ|運動|重量挙げ|person lifting weights|barbell|bodybuilder|deadlift|lifter|lifting|person|powerlifting|weight|weightlifter|weights|workout
🏋️‍♂️	1	重量挙げをする男|ウェイトリフティング|スポーツ|男|男性|man lifting weights|barbell|bodybuilder|deadlift|lifter|lifting|man|powerlifting|weight|weightlifter|weights|workout
🏋️‍♀️	1	重量挙げをする女|ウェイトリフティング|スポーツ|女|女性|woman lifting weights|barbell|bodybuilder|deadlift|lifter|lifting|powerlifting|weight|weightlifter|weights|woman|workout
🚴	1	自転車に乗る人|サイクリスト|サイクリング|スポーツ|ロードバイク|ロードレース|競輪|自転車|自転車乗り|person biking|bicycle|bicyclist|bike|biking|cycle|cyclist|person|riding|sport
🚴‍♂️	1	自転車に乗る男|サイクリング|スポーツ|ロードレース|男性|競輪|man biking|bicycle|bicyclist|bike|biking|cycle|cyclist|man|riding|sport
🚴‍♀️	1	自転車に乗る女|サイクリスト|サイクリング|スポーツ|ロードレース|女|女性|競輪|自転車|自転車乗り|woman biking|bicycle|bicyclist|bike|biking|cycle|cyclist|riding|sport|woman
🚵	1	マウンテンバイクに乗る人|サイクリスト|サイクリング|スポーツ|マウンテンバイク|山|競技|自転車|自転車乗り|person mountain biking|bicycle|bicyclist|bike|biking|cycle|cyclist|mountain|person|riding|sport
🚵‍♂️	1	マウンテンバイクに乗る男|サイクリング|スポーツ|男|男性|自転車|man mountain biking|bicycle|bicyclist|bike|biking|cycle|cyclist|man|mountain|riding|sport
🚵‍♀️	1	マウンテンバイクに乗る女|サイクリスト|サイクリング|スポーツ|女|女性|自転車|自転車乗り|woman mountain biking|bicycle|bicyclist|bike|biking|cycle|cyclist|mountain|riding|sport|woman
🤸	1	側転する人|スポーツ|トンボ返り|体操|側転|幸せ|活動的|活発|興奮|person cartwheeling|active|cartwheel|cartwheeling|excited|flip|gymnastics|happy|person|somersault
🤸‍♂️	1	側転する男|スポーツ|トンボ返り|体操|側転|幸せ|活発|男性|興奮|man cartwheeling|active|cartwheel|cartwheeling|excited|flip|gymnastics|happy|man|somersault
🤸‍♀️	1	側転する女|スポーツ|トンボ返り|体操|側転|女性|幸せ|活発|興奮|woman cartwheeling|active|cartwheel|cartwheeling|excited|flip|gymnastics|happy|somersault|woman
🤼	1	レスリングする人|スポーツ|デュエル|リング内|レスラー|レスリング|レスリング大会|取っ組み合い|格闘|people wrestling|combat|duel|grapple|people|ring|tournament|wrestle|wrestling
🤼‍♂️	1	レスリングする男|スポーツ|デュエル|リング内|レスリング|レスリングをする|レスリング大会|取っ組み合い|格闘|男性|men wrestling|combat|duel|grapple|men|ring|tournament|wrestle|wrestling
🤼‍♀️	1	レスリングする女|スポーツ|デュエル|リング内|レスリング|レスリングをする|レスリング大会|取っ組み合い|女性|格闘|women wrestling|combat|duel|grapple|ring|tournament|women|wrestle|wrestling
🤽	1	水球をする人|スポーツ|水上競技|水泳|水球|球技|person playing water polo|person|playing|polo|sport|swimming|water|waterpolo
🤽‍♂️	1	水球をする男|スポーツ|水上競技|水泳|水球|球技|男性|man playing water polo|man|playing|polo|sport|swimming|water|waterpolo
🤽‍♀️	1	水球をする女|スポーツ|女性|水上競技|水泳|水球|球技|woman playing water polo|playing|polo|sport|swimming|water|waterpolo|woman
🤾	1	ハンドボールをする人|アスリート|キャッチ|スポーツ|スローする|ハンドボール|ボール|投げる|放る|球技|person playing handball|athletics|ball|catch|chuck|handball|hurl|lob|person|pitch|playing|sport|throw|toss
🤾‍♂️	1	ハンドボールをする男|スポーツ|ハンドボール|球技|男|男性|man playing handball|athletics|ball|catch|chuck|handball|hurl|lob|man|pitch|playing|sport|throw|toss
🤾‍♀️	1	ハンドボールをする女|キャッチ|スポーツ|スローする|ハンドボール|ボール|女性|投げる|放る|球技|woman playing handball|athletics|ball|catch|chuck|handball|hurl|lob|pitch|playing|sport|throw|toss|woman
🤹	1	ジャグリングをする人|ジャグラー|ジャグリング|ジャグリングする人|バランス演技|マルチタスク|大道芸|操る|person juggling|act|balance|balancing|handle|juggle|juggling|manage|multitask|person|skill
🤹‍♂️	1	ジャグリングをする男|ジャグラー|ジャグリング|バランス演技|マルチタスク|大道芸|操る|男性|man juggling|act|balance|balancing|handle|juggle|juggling|man|manage|multitask|skill
🤹‍♀️	1	ジャグリングをする女|ジャグラー|ジャグリング|大道芸|女|女性|woman juggling|act|balance|balancing|handle|juggle|juggling|manage|multitask|skill|woman
🧘	1	ヨガのポーズをする人|くつろぎ|ヨガ|ヨガ行者|リラックス|平安|瞑想|禅|胡坐|蓮華座のポーズ|person in lotus position|cross|legged|legs|lotus|meditation|peace|person|position|relax|serenity|yoga|yogi|zen
🧘‍♂️	1	ヨガのポーズをする男|ヨガ|男|男性|瞑想|man in lotus position|cross|legged|legs|lotus|man|meditation|peace|position|relax|serenity|yoga|yogi|zen
🧘‍♀️	1	ヨガのポーズをする女|ヨガ|女|女性|瞑想|woman in lotus position|cross|legged|legs|lotus|meditation|peace|position|relax|serenity|woman|yoga|yogi|zen
🛀	1	風呂に入る人|バスタブ|浴槽|風呂|person taking bath|bath|bathtub|person|taking|tub
🛌	1	ベッドに寝る人|おやすみ|グーグー|ベッド|ホテル|寝る|就寝中|person in bed|bed|bedtime|good|goodnight|hotel|nap|night|person|sleep|tired|zzz
🧑‍🤝‍🧑	1	手をつなぐ2人|カップル|つなぐ|人|友達|恋人|手|手をつなぐ|people holding hands|bae|bestie|bff|couple|dating|flirt|friends|hand|hold|people|twins
👭	1	手をつなぐ女性|カップル|友情|友達|同性|女|姉妹|恋人|women holding hands|bae|bestie|bff|couple|dating|flirt|friends|girls|hand|hold|sisters|twins|women
👫	1	手をつなぐ男女|カップル|友達|女|女性|恋人|男|男女|男性|異性|woman and man holding hands|bae|bestie|bff|couple|dating|flirt|friends|hand|hold|man|twins|woman
👬	1	手をつなぐ男性|カップル|兄弟|友達|双子|同性|恋人|男|男性|men holding hands|bae|bestie|bff|boys|brothers|couple|dating|flirt|friends|hand|hold|men|twins
💏	1	2人でキス|カップル|キス|ちゅっ|ハート|記念日|kiss|anniversary|babe|bae|couple|date|dating|heart|love|mwah|person|romance|together|xoxo
👩‍❤️‍💋‍👨	1	2人でキス: 女性 男性|2人でキス|カップル|キス|ちゅっ|ハート|女性|男性|記念日|kiss: woman, man|anniversary|babe|bae|couple|date|dating|heart|kiss|love|man|mwah|person|romance|together|woman|xoxo
👨‍❤️‍💋‍👨	1	2人でキス: 男性 男性|2人でキス|カップル|キス|ちゅっ|ハート|男性|記念日|kiss: man, man|anniversary|babe|bae|couple|date|dating|heart|kiss|love|man|mwah|person|romance|together|xoxo
👩‍❤️‍💋‍👩	1	2人でキス: 女性 女性|2人でキス|カップル|キス|ちゅっ|ハート|女性|記念日|kiss: woman, woman|anniversary|babe|bae|couple|date|dating|heart|kiss|love|mwah|person|romance|together|woman|xoxo
💑	1	カップルとハート|カップル|ハート|恋人|熱々|記念日|couple with heart|anniversary|babe|bae|couple|dating|heart|kiss|love|person|relationship|romance|together|you
👩‍❤️‍👨	1	カップルとハート: 女性 男性|カップル|カップルとハート|ハート|女性|恋人|熱々|男性|記念日|couple with heart: woman, man|anniversary|babe|bae|couple|dating|heart|kiss|love|man|person|relationship|romance|together|woman|you
👨‍❤️‍👨	1	カップルとハート: 男性 男性|カップル|カップルとハート|ハート|恋人|熱々|男性|記念日|couple with heart: man, man|anniversary|babe|bae|couple|dating|heart|kiss|love|man|person|relationship|romance|together|you
👩‍❤️‍👩	1	カップルとハート: 女性 女性|カップル|カップルとハート|ハート|女性|恋人|熱々|記念日|couple with heart: woman, woman|anniversary|babe|bae|couple|dating|heart|kiss|love|person|relationship|romance|together|woman|you
👨‍👩‍👦	1	家族: 男性 女性 男の子|女性|子供|家族|男の子|男性|親子|family: man, woman, boy|boy|child|family|man|woman
👨‍👩‍👧	1	家族: 男性 女性 女の子|女の子|女性|子供|家族|男性|親子|family: man, woman, girl|child|family|girl|man|woman
👨‍👩‍👧‍👦	1	家族: 男性 女性 女の子 男の子|女の子|女性|子供|家族|男の子|男性|親子|family: man, woman, girl, boy|boy|child|family|girl|man|woman
👨‍👩‍👦‍👦	1	家族: 男性 女性 男の子 男の子|女性|子供|家族|男の子|男性|親子|family: man, woman, boy, boy|boy|child|family|man|woman
👨‍👩‍👧‍👧	1	家族: 男性 女性 女の子 女の子|女の子|女性|子供|家族|男性|親子|family: man, woman, girl, girl|child|family|girl|man|woman
👨‍👨‍👦	1	家族: 男性 男性 男の子|子供|家族|男の子|男性|親子|family: man, man, boy|boy|child|family|man
👨‍👨‍👧	1	家族: 男性 男性 女の子|女の子|子供|家族|男性|親子|family: man, man, girl|child|family|girl|man
👨‍👨‍👧‍👦	1	家族: 男性 男性 女の子 男の子|女の子|子供|家族|男の子|男性|親子|family: man, man, girl, boy|boy|child|family|girl|man
👨‍👨‍👦‍👦	1	家族: 男性 男性 男の子 男の子|子供|家族|男の子|男性|親子|family: man, man, boy, boy|boy|child|family|man
👨‍👨‍👧‍👧	1	家族: 男性 男性 女の子 女の子|女の子|子供|家族|男性|親子|family: man, man, girl, girl|child|family|girl|man
👩‍👩‍👦	1	家族: 女性 女性 男の子|女性|子供|家族|男の子|親子|family: woman, woman, boy|boy|child|family|woman
👩‍👩‍👧	1	家族: 女性 女性 女の子|女の子|女性|子供|家族|親子|family: woman, woman, girl|child|family|girl|woman
👩‍👩‍👧‍👦	1	家族: 女性 女性 女の子 男の子|女の子|女性|子供|家族|男の子|親子|family: woman, woman, girl, boy|boy|child|family|girl|woman
👩‍👩‍👦‍👦	1	家族: 女性 女性 男の子 男の子|女性|子供|家族|男の子|親子|family: woman, woman, boy, boy|boy|child|family|woman
👩‍👩‍👧‍👧	1	家族: 女性 女性 女の子 女の子|女の子|女性|子供|家族|親子|family: woman, woman, girl, girl|child|family|girl|woman
👨‍👦	1	家族: 男性 男の子|子供|家族|男の子|男性|親子|family: man, boy|boy|child|family|man
👨‍👦‍👦	1	家族: 男性 男の子 男の子|子供|家族|男の子|男性|親子|family: man, boy, boy|boy|child|family|man
👨‍👧	1	家族: 男性 女の子|女の子|子供|家族|男性|親子|family: man, girl|child|family|girl|man
👨‍👧‍👦	1	家族: 男性 女の子 男の子|女の子|子供|家族|男の子|男性|親子|family: man, girl, boy|boy|child|family|girl|man
👨‍👧‍👧	1	家族: 男性 女の子 女の子|女の子|子供|家族|男性|親子|family: man, girl, girl|child|family|girl|man
👩‍👦	1	家族: 女性 男の子|女性|子供|家族|男の子|親子|family: woman, boy|boy|child|family|woman
👩‍👦‍👦	1	家族: 女性 男の子 男の子|女性|子供|家族|男の子|親子|family: woman, boy, boy|boy|child|family|woman
👩‍👧	1	家族: 女性 女の子|女の子|女性|子供|家族|親子|family: woman, girl|child|family|girl|woman
👩‍👧‍👦	1	家族: 女性 女の子 男の子|女の子|女性|子供|家族|男の子|親子|family: woman, girl, boy|boy|child|family|girl|woman
👩‍👧‍👧	1	家族: 女性 女の子 女の子|女の子|女性|子供|家族|親子|family: woman, girl, girl|child|family|girl|woman
🗣️	1	話す人のシルエット|シルエット|話す|顔|speaking head|face|head|silhouette|speak|speaking
👤	1	人のシルエット|1人|シルエット|上半身|謎の人|bust in silhouette|bust|mysterious|shadow|silhouette
👥	1	2人のシルエット|2人|シルエット|上半身|友達|busts in silhouette|bff|bust|busts|everyone|friend|friends|people|silhouette
🫂	1	ハグする人|ありがとう|お別れ|こんにちは|さようなら|ハグ|友情|慰め|抱擁|挨拶|people hugging|comfort|embrace|farewell|friendship|goodbye|hello|hug|hugging|love|people|thanks
👪	1	家族|子供|親子|family|child
🧑‍🧑‍🧒	1	家族: 大人二人と子供一人|大人二人|子供一人|家族|親子|family: adult, adult, child|adult|child|family
🧑‍🧑‍🧒‍🧒	1	家族: 大人二人と子供二人|大人二人|子供二人|家族|親子|family: adult, adult, child, child|adult|child|family
🧑‍🧒	1	家族: 大人一人と子供一人|大人一人|子供一人|家族|親子|family: adult, child|adult|child|family
🧑‍🧒‍🧒	1	家族: 大人一人と子供二人|大人一人|子供二人|家族|親子|family: adult, child, child|adult|child|family
👣	1	足あと|裸足|足|footprints|barefoot|clothing|footprint|omw|print|walk
🐵	2	サルの顔|サル|動物|猿|顔|monkey face|animal|banana|face|monkey
🐒	2	サル|動物|猿|monkey|animal|banana
🦍	2	ゴリラ|動物|顔|gorilla|animal
🦧	2	オランウータン|サル|動物|森の人|猿|orangutan|animal|ape|monkey
🐶	2	イヌの顔|イヌ|ペット|動物|子犬|犬|顔|dog face|adorbs|animal|dog|face|pet|puppies|puppy
🐕	2	イヌ|ペット|動物|犬|dog|animal|animals|dogs|pet
🦮	2	盲導犬|アクセシビリティ|犬|視覚|補助|補助犬|障がい|guide dog|accessibility|animal|blind|dog|guide
🐕‍🦺	2	介助犬|アクセシビリティ|サービス|犬|補助|service dog|accessibility|animal|assistance|dog|service
🐩	2	プードル|ペット|動物|犬|poodle|animal|dog|fluffy
🐺	2	オオカミの顔|オオカミ|動物|狼|顔|wolf|animal|face
🦊	2	キツネの顔|キツネ|動物|狐|顔|fox|animal|face
🦝	2	アライグマ|ずる賢い|動物|好奇心|詮索好き|raccoon|animal|curious|sly
🐱	2	ネコの顔|ネコ|ペット|動物|子猫|猫|顔|cat face|animal|cat|face|kitten|kitty|pet
🐈	2	ネコ|ペット|動物|猫|cat|animal|animals|cats|kitten|pet
🐈‍⬛	2	黒猫|ネコ|不吉|動物|猫|黒|black cat|animal|black|cat|feline|halloween|meow|unlucky
🦁	2	ライオンの顔|しし座|ライオン|動物|星座|顔|lion|alpha|animal|face|leo|mane|order|rawr|roar|safari|strong|zodiac
🐯	2	トラの顔|トラ|動物|虎|顔|tiger face|animal|big|cat|face|predator|tiger
🐅	2	トラ|タイガー|動物|肉食動物|虎|tiger|animal|big|cat|predator|zoo
🐆	2	ヒョウ|動物|肉食動物|leopard|animal|big|cat|predator|zoo
🐴	2	馬の顔|ウマ|動物|顔|馬|horse face|animal|dressage|equine|face|farm|horse|horses
🫎	2	ヘラジカ|エルク|ムース|動物|哺乳類|枝角|鹿|moose|alces|animal|antlers|elk|mammal
🫏	2	ロバ|ケッテイ|ラバ|動物|哺乳類|馬|donkey|animal|ass|burro|hinny|mammal|mule|stubborn
🐎	2	馬|ウマ|ダービー|乗馬|動物|家畜|競走馬|競馬|horse|animal|equestrian|farm|racehorse|racing
🦄	2	ユニコーンの顔|ユニコーン|一角獣|動物|顔|unicorn|face
🦓	2	シマウマ|しましま|ゼブラ|動物|縞|zebra|animal|stripe
🦌	2	シカ|動物|顔|鹿|deer|animal
🦬	2	バイソン|バッファロー|動物|牛|群れ|野牛|bison|animal|buffalo|herd|wisent
🐮	2	牛の顔|ウシ|動物|牛|顔|cow face|animal|cow|face|farm|milk|moo
🐂	2	牡牛|ウシ|おうし座|動物|星座|牛|ox|animal|animals|bull|farm|taurus|zodiac
🐃	2	水牛|バッファロー|動物|牛|water buffalo|animal|buffalo|water|zoo
🐄	2	牝牛|ウシ|乳牛|動物|家畜|牛|cow|animal|animals|farm|milk|moo
🐷	2	ブタの顔|ブタ|動物|豚|顔|pig face|animal|bacon|face|farm|pig|pork
🐖	2	ブタ|ポーク|動物|家畜|豚|pig|animal|bacon|farm|pork|sow
🐗	2	イノシシ|動物|猪|顔|boar|animal|pig
🐽	2	ブタ鼻|ブタ|動物|豚|顔|鼻|pig nose|animal|face|farm|nose|pig|smell|snout
🐏	2	牡羊|おひつじ座|ヒツジ|動物|星座|羊|ram|animal|aries|horns|male|sheep|zodiac|zoo
🐑	2	羊|ウール|ヒツジ|動物|家畜|羊毛|ewe|animal|baa|farm|female|fluffy|lamb|sheep|wool
🐐	2	山羊|ヤギ|やぎ座|動物|家畜|星座|goat|animal|capricorn|farm|milk|zodiac
🐪	2	ラクダ|コブ|ヒトコブラクダ|動物|砂漠|camel|animal|desert|dromedary|hump|one
🐫	2	フタコブラクダ|コブ|フタコブ|ラクダ|動物|砂漠|two-hump camel|animal|bactrian|camel|desert|hump|two|two-hump
🦙	2	ラマ|アルパカ|ウール|グアナコ|ビクーナ|動物|羊毛|llama|alpaca|animal|guanaco|vicuña|wool
🦒	2	キリン|動物|giraffe|animal|spots
🐘	2	ゾウ|動物|象|elephant|animal
🦣	2	マンモス|動物|巨大|牙|絶滅|mammoth|animal|extinction|large|tusk|wooly
🦏	2	サイ|動物|顔|rhinoceros|animal
🦛	2	カバ|動物|hippopotamus|animal|hippo
🐭	2	ネズミの顔|ねずみ|ネズミ|動物|顔|mouse face|animal|face|mouse
🐁	2	ハツカネズミ|ねずみ|ネズミ|マウス|動物|mouse|animal|animals
🐀	2	ネズミ|ねずみ|ラット|動物|rat|animal
🐹	2	ハムスターの顔|ネズミ|ハムスター|ペット|動物|顔|hamster|animal|face|pet
🐰	2	ウサギの顔|うさぎ|ウサギ|ペット|動物|顔|rabbit face|animal|bunny|face|pet|rabbit
🐇	2	ウサギ|うさぎ|ペット|動物|rabbit|animal|bunny|pet
🐿️	2	リス|シマリス|りす|動物|chipmunk|animal|squirrel
🦫	2	ビーバー|ダム作り|動物|beaver|animal|dam|teeth
🦔	2	ハリネズミ|とげ|はりねずみ|ヤマアラシ|動物|hedgehog|animal|spiny
🦇	2	コウモリ|こうもり|バンパイア|動物|吸血鬼|bat|animal|vampire
🐻	2	クマの顔|くま|クマ|動物|熊|顔|bear|animal|face|grizzly|growl|honey
🐻‍❄️	2	シロクマ|クマ|ホッキョクグマ|動物|北極|白|polar bear|animal|arctic|bear|polar|white
🐨	2	コアラ|動物|顔|koala|animal|australia|bear|down|face|marsupial|under
🐼	2	パンダの顔|パンダ|動物|顔|panda|animal|bamboo|face
🦥	2	ナマケモノ|のんびり|ものぐさ|動物|sloth|lazy|slow
🦦	2	カワウソ|ラッコ|動物|遊び好き|魚を食べる|otter|animal|fishing|playful
🦨	2	スカンク|動物|悪臭|skunk|animal|stink
🦘	2	カンガルー|オーストラリア|ジャンプ|ジョーイ|動物|有袋類|kangaroo|animal|joey|jump|marsupial
🦡	2	アナグマ|ミツアナグマ|ラーテル|動物|badger|animal|honey|pester
🐾	2	肉球|動物|犬|猫|足跡|paw prints|feet|paw|paws|print|prints
🦃	2	七面鳥|ターキー|動物|鳥|turkey|bird|gobble|thanksgiving
🐔	2	にわとり|ニワトリ|動物|顔|鳥|chicken|animal|bird|ornithology
🐓	2	おんどり|にわとり|ニワトリ|動物|鳥|rooster|animal|bird|ornithology
🐣	2	卵からかえったひよこ|ひな|ひよこ|動物|卵|顔|鳥|hatching chick|animal|baby|bird|chick|egg|hatching
🐤	2	ひよこ|ひな|動物|横を向いているひよこ|顔|鳥|baby chick|animal|baby|bird|chick|ornithology
🐥	2	前を向いているひよこ|ひな|ひよこ|動物|鳥|front-facing baby chick|animal|baby|bird|chick|front-facing|newborn|ornithology
🐦	2	鳥|動物|顔|bird|animal|ornithology
🐧	2	ペンギン|動物|顔|鳥|penguin|animal|antarctica|bird|ornithology
🕊️	2	ハト|動物|平和|鳥|鳩|dove|bird|fly|ornithology|peace
🦅	2	ワシ|イーグル|動物|鳥|eagle|animal|bird|ornithology
🦆	2	カモ|動物|鳥|duck|animal|bird|ornithology
🦢	2	白鳥|ひな|みにくいアヒルの子|動物|鳥|swan|animal|bird|cygnet|duckling|ornithology|ugly
🦉	2	フクロウ|ミミズク|動物|鳥|owl|animal|bird|ornithology|wise
🦤	2	ドードー|モーリシャス島|絶滅|飛べない鳥|鳥|dodo|animal|bird|extinction|large|ornithology
🪶	2	羽|羽根|羽毛|軽い|飛ぶ|鳥|feather|bird|flight|light|plumage
🦩	2	フラミンゴ|ピンク色|動物|派手|熱帯|鳥|flamingo|animal|bird|flamboyant|ornithology|tropical
🦚	2	クジャク|ピーコック|孔雀|派手|鳥|peacock|animal|bird|colorful|ornithology|ostentatious|peahen|pretty|proud
🦜	2	オウム|しゃべる|動物|海賊|鳥|parrot|animal|bird|ornithology|pirate|talk
🪽	2	翼|天使|天国|神話|空|羽|飛び立つ|飛ぶ|飛行|鳥|wing|angelic|ascend|aviation|bird|fly|flying|heavenly|mythology|soar
🐦‍⬛	2	黒い鳥|カーカー|カラス|くちばし|コクチョウ|ミヤマガラス|ワタリガラス|烏|鳥|鳥類|黒|black bird|animal|beak|bird|black|caw|corvid|crow|ornithology|raven|rook
🪿	2	ガチョウ|アヒル|ガーガー|まぬけ|鳥|鳥類|goose|animal|bird|duck|flock|fowl|gaggle|gander|geese|honk|ornithology|silly
🐦‍🔥	2	フェニックス|ファンタジー|不死鳥|再生|復活|火の鳥|生まれ変わり|神話|転生|輪廻|phoenix|ascend|ascension|emerge|fantasy|firebird|glory|immortal|rebirth|reincarnation|reinvent|renewal|revival|revive|rise|transform
🐸	2	カエルの顔|カエル|動物|顔|frog|animal|face
🐊	2	ワニ|クロコダイル|動物|爬虫類|crocodile|animal|zoo
🐢	2	カメ|亀|動物|爬虫類|turtle|animal|terrapin|tortoise
🦎	2	トカゲ|ヤモリ|動物|爬虫類|lizard|animal|reptile
🐍	2	ヘビ|へびつかい座|動物|星座|毒|爬虫類|snake|animal|bearer|ophiuchus|serpent|zodiac
🐲	2	ドラゴンの顔|ドラゴン|動物|竜|顔|龍|dragon face|animal|dragon|face|fairy|fairytale|tale
🐉	2	ドラゴン|動物|竜|龍|dragon|animal|fairy|fairytale|knights|tale
🦕	2	草食恐竜|ディプロドクス|ブラキオサウルス|ブロントサウルス|恐竜|竜脚類|sauropod|brachiosaurus|brontosaurus|dinosaur|diplodocus
🦖	2	ティラノサウルス|恐竜|t-rex|dinosaur|rex|t|tyrannosaurus
🐳	2	潮吹きクジラ|クジラ|動物|潮吹き|spouting whale|animal|beach|face|ocean|spouting|whale
🐋	2	クジラ|動物|whale|animal|beach|ocean
🐬	2	イルカ|ドルフィン|動物|dolphin|animal|beach|flipper|ocean
🦭	2	アザラシ|アシカ|オットセイ|トド|動物|海獣|seal|animal|lion|ocean|sea
🐟	2	魚|うお座|フィッシング|動物|星座|釣り|fish|animal|dinner|fishes|fishing|pisces|zodiac
🐠	2	熱帯魚|動物|魚|tropical fish|animal|fish|fishes|tropical
🐡	2	フグ|動物|魚|blowfish|animal|fish
🦈	2	サメ|動物|魚|shark|animal|fish
🐙	2	タコ|オクトパス|動物|軟体動物|octopus|animal|creature|ocean
🐚	2	巻き貝|動物|貝|貝殻|spiral shell|animal|beach|conch|sea|shell|spiral
🪸	2	サンゴ|気候変動|海|珊瑚礁|coral|change|climate|ocean|reef|sea
🪼	2	クラゲ|プランクトン|刺す|毒|水族館|海|海洋生物|無脊椎動物|触手|針|jellyfish|animal|aquarium|burn|invertebrate|jelly|life|marine|ocean|ouch|plankton|sea|sting|stinger|tentacles
🦀	2	カニ|動物|星座|crab|cancer|zodiac
🦞	2	ザリガニ|シーフード|はさみ|ビスク|レッドロブスター|ロブスター|動物|lobster|animal|bisque|claws|seafood
🦐	2	エビ|シーフード|シュリンプ|動物|小エビ|shrimp|food|shellfish|small
🦑	2	イカ|シーフード|動物|軟体動物|squid|animal|food|mollusk
🦪	2	牡蠣|オイスター|カキ|シーフード|ダイビング|生ガキ|真珠|貝|魚介|oyster|diving|pearl
🐌	2	かたつむり|エスカルゴ|カタツムリ|でんでん虫|虫|snail|animal|escargot|garden|nature|slug
🦋	2	チョウ|バタフライ|昆虫|虫|蝶|butterfly|insect|pretty
🐛	2	毛虫|イモムシ|芋虫|虫|bug|animal|garden|insect
🐜	2	アリ|昆虫|虫|ant|animal|garden|insect
🐝	2	ミツバチ|ハチ|虫|蜂|honeybee|animal|bee|bumblebee|honey|insect|nature|spring
🪲	2	カブトムシ|つの|昆虫|虫|beetle|animal|bug|insect
🐞	2	テントウムシ|てんとう虫|虫|lady beetle|animal|beetle|garden|insect|lady|ladybird|ladybug|nature
🦗	2	バッタ|コオロギ|昆虫|直翅目|虫|cricket|animal|bug|grasshopper|insect|orthoptera
🪳	2	ゴキブリ|害虫|虫|cockroach|animal|insect|pest|roach
🕷️	2	クモ|スパイダー|虫|spider|animal|insect
🕸️	2	クモの巣|ウェブ|クモ|スパイダー|spider web|spider|web
🦂	2	サソリ|さそり座|星座|虫|scorpion|scorpio|scorpius|zodiac
🦟	2	蚊|ウイルス|マラリア|刺す|熱|病気|虫|mosquito|bite|disease|fever|insect|malaria|pest|virus
🪰	2	ハエ|不潔|害虫|病原菌|虫|fly|animal|disease|insect|maggot|pest|rotting
🪱	2	ミミズ|寄生虫|虫|worm|animal|annelid|earthworm|parasite
🦠	2	微生物|アメーバ|ウイルス|バクテリア|科学|細菌|microbe|amoeba|bacteria|science|virus
💐	2	花束|ブーケ|植物|花|bouquet|anniversary|birthday|date|flower|love|plant|romance
🌸	2	桜|サクラ|植物|花|開花|cherry blossom|blossom|cherry|flower|plant|spring|springtime
💮	2	大変よくできました|はなまる|よくできました|花|white flower|flower|white
🪷	2	ハスの花|ヒンドゥー教|仏教|極楽|清浄|花|lotus|beauty|buddhism|calm|flower|hinduism|peace|purity|serenity
🏵️	2	花飾り|植物|花|rosette|plant
🌹	2	バラ|ローズ|植物|花|薔薇|赤いバラ|rose|beauty|elegant|flower|love|plant|red|valentine
🥀	2	しおれた花|しおれた|花|wilted flower|dying|flower|wilted
🌺	2	ハイビスカス|植物|花|hibiscus|flower|plant
🌻	2	ヒマワリ|ひまわり|花|sunflower|flower|outdoors|plant|sun
🌼	2	開花|キンポウゲ|咲いた花|植物|花|blossom|buttercup|dandelion|flower|plant
🌷	2	チューリップ|植物|花|開花|tulip|blossom|flower|growth|plant
🪻	2	ヒヤシンス|スミレ色|ブルーボネット|ライラック|ラベンダー|植物|紫色|花|青紫|hyacinth|bloom|bluebonnet|flower|indigo|lavender|lilac|lupine|plant|purple|shrub|snapdragon|spring|violet
🌱	2	新芽|双葉|芽|seedling|plant|sapling|sprout|young
🪴	2	鉢植え|ベランダ|植木鉢|植物|水やり|緑|苗|観葉植物|potted plant|decor|grow|house|nurturing|plant|pot|potted
🌲	2	常緑樹|木|森|evergreen tree|christmas|evergreen|forest|pine|tree
🌳	2	落葉樹|木|森|deciduous tree|deciduous|forest|green|habitat|shedding|tree
🌴	2	ヤシの木|ビーチ|ヤシ|木|熱帯|palm tree|beach|palm|plant|tree|tropical
🌵	2	サボテン|植物|砂漠|cactus|desert|drought|nature|plant
🌾	2	稲|ススキ|稲穂|穀物|米|草|sheaf of rice|ear|grain|grains|plant|rice|sheaf
🌿	2	ハーブ|草|葉|薬草|香草|herb|leaf|plant
☘️	2	クローバー|植物|草|葉|shamrock|irish|plant
🍀	2	四つ葉のクローバー|4|クローバー|植物|草|葉|four leaf clover|clover|four|four-leaf|irish|leaf|lucky|plant
🍁	2	かえで|カエデ|もみじ|紅葉|落ち葉|葉|maple leaf|falling|leaf|maple
🍂	2	落ち葉|木の葉|枯れ葉|葉|葉っぱ|fallen leaf|autumn|fall|fallen|falling|leaf
🍃	2	風に揺れる葉|木の葉|落ち葉|葉|葉っぱ|風|leaf fluttering in wind|blow|flutter|fluttering|leaf|wind
🪹	2	空っぽの巣|営巣|巣|巣作り|empty nest|branch|empty|home|nest|nesting
🪺	2	鳥の卵と巣|卵|営巣|巣|巣作り|鳥の巣|nest with eggs|bird|branch|egg|eggs|nest|nesting
🍄	2	キノコ|きのこ|マッシュルーム|毒|菌類|野菜|mushroom|fungus|toadstool
🍇	3	ぶどう|グレープ|ブドウ|果物|grapes|dionysus|fruit|grape
🍈	3	メロン|マスクメロン|果物|野菜|melon|cantaloupe|fruit
🍉	3	スイカ|果物|野菜|watermelon|fruit
🍊	3	みかん|オレンジ|ネクタリン|ビタミンｃ|果物|柑橘類|tangerine|c|citrus|fruit|nectarine|orange|vitamin
🍋	3	レモン|果物|柑橘類|酸っぱい|lemon|citrus|fruit|sour
🍋‍🟩	3	ライム|さわやか|トロピカル|フルーツ|マルガリータ|モヒート|果物|柑橘類|緑|酸っぱい|lime|acidity|citrus|cocktail|fruit|garnish|key|margarita|mojito|refreshing|salsa|sour|tangy|tequila|tropical|zest
🍌	3	バナナ|カリウム|果物|banana|fruit|potassium
🍍	3	パイナップル|トロピカル|パイン|果物|pineapple|colada|fruit|pina|tropical
🥭	3	マンゴー|トロピカル|フルーツ|果物|食べ物|mango|food|fruit|tropical
🍎	3	赤リンゴ|アップル|りんご|果物|red apple|apple|diet|food|fruit|health|red|ripe
🍏	3	青リンゴ|アップル|りんご|果物|green apple|apple|fruit|green
🍐	3	洋ナシ|ナシ|果物|西洋ナシ|pear|fruit
🍑	3	桃|ピーチ|果物|peach|fruit
🍒	3	さくらんぼ|チェリー|果物|cherries|berries|cherry|fruit|red
🍓	3	いちご|ストロベリー|果物|苺|strawberry|berry|fruit
🫐	3	ブルーベリー|ビルベリー|ベリー|果物|食べ物|blueberries|berries|berry|bilberry|blue|blueberry|food|fruit
🥝	3	キウイフルーツ|キウイ|果物|kiwi fruit|food|fruit|kiwi
🍅	3	トマト|果物|野菜|tomato|food|fruit|vegetable
🫒	3	オリーブ|実|果実|食べ物|olive|food
🥥	3	ココナツ|ココナッツ|ヤシの実|果物|coconut|colada|palm|piña
🥑	3	アボカド|果物|野菜|avocado|food|fruit
🍆	3	ナス|野菜|eggplant|aubergine|vegetable
🥔	3	ジャガイモ|イモ|じゃがいも|ポテト|野菜|potato|food|vegetable
🥕	3	人参|ニンジン|野菜|carrot|food|vegetable
🌽	3	とうもろこし|コーン|トウモロコシ|野菜|ear of corn|corn|crops|ear|farm|maize|maze
🌶️	3	とうがらし|辛い|野菜|香辛料|hot pepper|hot|pepper
"""

private const val UNICODE_EMOJI_DATA_2: String = """
🫑	3	ピーマン|とうがらし|パプリカ|野菜|青ピーマン|bell pepper|bell|capsicum|food|pepper|vegetable
🥒	3	キュウリ|ピクルス|漬け物|野菜|cucumber|food|pickle|vegetable
🥬	3	葉野菜|キャベツ|ケール|サラダ|チンゲン菜|レタス|leafy green|bok|burgers|cabbage|choy|green|kale|leafy|lettuce|salad
🥦	3	ブロッコリー|野菜|broccoli|cabbage|wild
🧄	3	ニンニク|ガーリック|におい|薬味|野菜|香り|garlic|flavoring
🧅	3	タマネギ|ねぎ|玉ねぎ|薬味|野菜|onion|flavoring
🥜	3	ピーナッツ|ナッツ|落花生|野菜|peanuts|food|nut|peanut|vegetable
🫘	3	豆|キドニービーンズ|ビーンズ|レッドキドニー|小豆|赤インゲン豆|金時豆|食べ物|beans|food|kidney|legume|small
🌰	3	くり|栗|野菜|chestnut|almond|plant
🫚	3	ショウガ|ジンジャー|スパイス|ハーブ|生姜|薬味|薬草|ginger root|beer|ginger|health|herb|natural|root|spice
🫛	3	エンドウ豆|グリーンピース|さや|大豆|枝豆|豆|野菜|pea pod|beans|beanstalk|edamame|legume|pea|pod|soybean|vegetable|veggie
🍄‍🟫	3	きのこ|キノコ|しいたけ|トリュフ|ブラウンマッシュルーム|ポートベロー|マッシュルーム|椎茸|茸|菌類|brown mushroom|food|fungi|fungus|mushroom|nature|pizza|portobello|shiitake|shroom|spore|sprout|toppings|truffle|vegetable|vegetarian|veggie
🍞	3	食パン|パン|ベーカリー|小麦|bread|carbs|food|grain|loaf|restaurant|toast|wheat
🥐	3	クロワッサン|パン|フランス|ベーカリー|croissant|bread|breakfast|crescent|food|french|roll
🥖	3	バゲット|パン|フランスパン|ベーカリー|baguette bread|baguette|bread|food|french
🫓	3	フラットブレッド|アレパ|ナン|パン|ピタパン|ラバシ|食べ物|flatbread|arepa|bread|food|gordita|lavash|naan|pita
🥨	3	プレッツェル|ねじり|パン|pretzel|convoluted|twisted
🥯	3	ベーグル|クリームチーズ|パン|パン屋|ベーカリー|朝食|bagel|bakery|bread|breakfast|schmear
🥞	3	パンケーキ|ホットケーキ|pancakes|breakfast|crêpe|food|hotcake|pancake
🧇	3	ワッフル|ふんわり|朝食|焼き菓子|waffle|breakfast|indecisive|iron
🧀	3	チーズ|cheese wedge|cheese|wedge
🍖	3	骨付き肉|もも肉|肉|骨|meat on bone|bone|meat
🍗	3	鶏もも肉|チキン|もも肉|肉|骨|骨付き肉|poultry leg|bone|chicken|drumstick|hungry|leg|poultry|turkey
🥩	3	ステーキ肉|ステーキ|ビーフ|ポーク|ラム|肉|赤身肉|cut of meat|chop|cut|lambchop|meat|porkchop|red|steak
🥓	3	ベーコン|肉|bacon|breakfast|food|meat
🍔	3	ハンバーガー|バーガー|ファストフード|hamburger|burger|eat|fast|food|hungry
🍟	3	フライドポテト|ファストフード|フレンチフライ|ポテト|ポテトフライ|french fries|fast|food|french|fries
🍕	3	ピザ|チーズ|ピッツァ|ペパロニ|pizza|cheese|food|hungry|pepperoni|slice
🌭	3	ホットドッグ|ソーセージ|フランクフルト|hot dog|dog|frankfurter|hot|hotdog|sausage
🥪	3	サンドイッチ|パン|sandwich|bread
🌮	3	タコス|メキシカン|メキシコ料理|taco|mexican
🌯	3	ブリトー|ソフトタコス|メキシカン|メキシコ料理|ラップサンド|burrito|mexican|wrap
🫔	3	タマル|タマレス|ちまき|パモーニャ|メキシカン|食べ物|tamale|food|mexican|pamonha|wrapped
🥙	3	ケバブサンド|ケバブ|ピタパン|ラップサンド|stuffed flatbread|falafel|flatbread|food|gyro|kebab|stuffed
🧆	3	ファラフェル|コロッケ|ヒヨコマメ|肉団子|falafel|chickpea|meatball
🥚	3	卵|egg|breakfast|food
🍳	3	料理|フライパン|レストラン|卵|朝食|目玉焼き|cooking|breakfast|easy|egg|fry|frying|over|pan|restaurant|side|sunny|up
🥘	3	パエリア|シーフード|パエリア鍋|shallow pan of food|casserole|food|paella|pan|shallow
🍲	3	なべ|シチュー|スープ|煮物|煮込み|pot of food|food|pot|soup|stew
🫕	3	フォンデュ|スイス|チーズ|チーズフォンデュ|チョコ|溶かす|鍋|食べ物|fondue|cheese|chocolate|food|melted|pot|ski
🥣	3	ボウルとスプーン|オートミール|おかゆ|シリアル|朝食|粥|bowl with spoon|bowl|breakfast|cereal|congee|oatmeal|porridge|spoon
🥗	3	グリーンサラダ|グリーン|サラダ|green salad|food|green|salad
🍿	3	ポップコーン|お菓子|popcorn|corn|movie|pop
🧈	3	バター|乳製品|butter|dairy
🧂	3	塩|しょっぱい|振りかけ容器|薬味|調味料|salt|condiment|flavor|mad|salty|shaker|taste|upset
🥫	3	缶詰|缶|非常食|canned food|can|canned|food
🍱	3	弁当|幕の内|弁当箱|駅弁|bento box|bento|box|food
🍘	3	せんべい|お菓子|rice cracker|cracker|food|rice
🍙	3	おにぎり|おむすび|和食|米|rice ball|ball|food|japanese|rice
🍚	3	ごはん|ライス|米|cooked rice|cooked|food|rice
🍛	3	カレーライス|カレー|ライス|curry rice|curry|food|rice
🍜	3	ラーメン|スープ|どんぶり|ヌードル|熱々|箸|麺|steaming bowl|bowl|chopsticks|food|noodle|pho|ramen|soup|steaming
🍝	3	スパゲッティ|イタリア料理|スパゲッティー|スパゲティ|パスタ|ミートソース|spaghetti|food|meatballs|pasta|restaurant
🍠	3	焼き芋|サツマイモ|芋|roasted sweet potato|food|potato|roasted|sweet
🍢	3	おでん|串|練り物|oden|food|kebab|restaurant|seafood|skewer|stick
🍣	3	すし|お寿司|寿司|sushi|food
🍤	3	エビフライ|エビ|エビ天|フライ|天ぷら|fried shrimp|fried|prawn|shrimp|tempura
🍥	3	なると|渦巻き|練り物|fish cake with swirl|cake|fish|food|pastry|restaurant|swirl
🥮	3	月餅|お祝い|中秋節|中華菓子|秋|moon cake|autumn|cake|festival|moon|yuèbǐng
🍡	3	だんご|お団子|デザート|串|和菓子|dango|dessert|japanese|skewer|stick|sweet
🥟	3	点心|ギョーザ|ぎょうざ|肉まん|餃子|dumpling|empanada|gyōza|jiaozi|pierogi|potsticker
🥠	3	フォーチュンクッキー|おみくじ|fortune cookie|cookie|fortune|prophecy
🥡	3	テイクアウト弁当|デリバリー|中華|弁当|持ち帰り|箸|takeout box|box|chopsticks|delivery|food|oyster|pail|takeout
🍦	3	ソフトクリーム|アイス|スイーツ|ソフト|デザート|soft ice cream|cream|dessert|food|ice|icecream|restaurant|serve|soft|sweet
🍧	3	かき氷|アイス|シェイブアイス|スイーツ|デザート|氷|shaved ice|dessert|ice|restaurant|shaved|sweet
🍨	3	アイスクリーム|アイス|スイーツ|デザート|ice cream|cream|dessert|food|ice|restaurant|sweet
🍩	3	ドーナツ|お菓子|スイーツ|デザート|doughnut|breakfast|dessert|donut|food|sweet
🍪	3	クッキー|お菓子|スイーツ|チョコレートチップ|デザート|cookie|chip|chocolate|dessert|sweet
🎂	3	バースデーケーキ|お祝い|お菓子|ケーキ|スイーツ|バースデー|ハッピーバースデー|誕生日|birthday cake|bday|birthday|cake|celebration|dessert|happy|pastry|sweet
🍰	3	ショートケーキ|お菓子|ケーキ|スイーツ|デザート|shortcake|cake|dessert|pastry|slice|sweet
🧁	3	カップケーキ|おやつ|お菓子|ケーキ|スイーツ|ベーカリー|砂糖|cupcake|bakery|dessert|sprinkles|sugar|sweet|treat
🥧	3	パイ|アップルパイ|お菓子|タルト|パンプキンパイ|ミートパイ|pie|apple|filling|fruit|meat|pastry|pumpkin|slice
🍫	3	チョコレート|お菓子|スイーツ|チョコ|デザート|板チョコ|chocolate bar|bar|candy|chocolate|dessert|halloween|sweet|tooth
🍬	3	キャンディ|あめ|お菓子|虫歯|candy|cavities|dessert|halloween|restaurant|sweet|tooth|wrapper
🍭	3	ぺろぺろキャンディ|あめ|お菓子|キャンディ|棒付きキャンディ|lollipop|candy|dessert|food|restaurant|sweet
🍮	3	プリン|お菓子|カスタード|スイーツ|デザート|custard|dessert|pudding|sweet
🍯	3	はちみつ|ハチミツ|ハニー|プーさん|壺|甘い|honey pot|barrel|bear|food|honey|honeypot|jar|pot|sweet
🍼	3	ほにゅう瓶|ミルク|哺乳瓶|新生児|赤ちゃん|baby bottle|babies|baby|birth|born|bottle|drink|infant|milk|newborn
🥛	3	牛乳入りのコップ|グラス|コップ|ミルク|牛乳|glass of milk|drink|glass|milk
☕	3	温かい飲み物|カフェ|カフェイン|コーヒー|ホットドリンク|紅茶|飲み物|hot beverage|beverage|cafe|caffeine|chai|coffee|drink|hot|morning|steaming|tea
🫖	3	ティーポット|お茶|急須|茶器|飲み物|teapot|brew|drink|food|pot|tea
🍵	3	湯飲み|お茶|緑茶|茶碗|飲み物|teacup without handle|beverage|cup|drink|handle|oolong|tea|teacup
🍶	3	徳利|日本酒|猪口|酒|sake|bar|beverage|bottle|cup|drink|restaurant
🍾	3	シャンパン|コルク|シャンペン|スパークリングワイン|スプマンテ|バー|ボトル|泡|飲み物|bottle with popping cork|bar|bottle|cork|drink|popping
🍷	3	ワイングラス|アルコール|お酒|グラス|バー|ワイン|wine glass|alcohol|bar|beverage|booze|club|drink|drinking|drinks|glass|restaurant|wine
🍸	3	カクテルグラス|アルコール|お酒|カクテル|グラス|クラブ|バー|マティーニ|cocktail glass|alcohol|bar|booze|club|cocktail|drink|drinking|drinks|glass|mad|martini|men
🍹	3	トロピカルドリンク|アルコール|お酒|カクテル|トロピカル|バー|マイタイ|tropical drink|alcohol|bar|booze|club|cocktail|drink|drinking|drinks|drunk|mai|party|tai|tropical|tropics
🍺	3	ビールジョッキ|アルコール|オクトーバーフェスト|お酒|ジョッキ|バー|ビール|居酒屋|beer mug|alcohol|ale|bar|beer|booze|drink|drinking|drinks|mug|octoberfest|oktoberfest|pint|stein|summer
🍻	3	ビールで乾杯|アルコール|ジョッキ|バー|ビール|乾杯|宴会|clinking beer mugs|alcohol|bar|beer|booze|bottoms|cheers|clink|clinking|drinking|drinks|mugs
🥂	3	グラスで乾杯|グラス|シャンパン|シャンペン|スパークリングワイン|スプマンテ|乾杯|祝杯|clinking glasses|celebrate|clink|clinking|drink|glass|glasses
🥃	3	タンブラーグラス|ウィスキー|ウイスキー|グラス|ショット|スコッチ|タンブラー|tumbler glass|glass|liquor|scotch|shot|tumbler|whiskey|whisky
🫗	3	コップから注ぐ|ガラス|こぼす|水|液体|飲み物|pouring liquid|accident|drink|empty|glass|liquid|oops|pour|pouring|spill|water
🥤	3	ストローカップ|コップ|ジュース|ソーダ|ソフトドリンク|水|cup with straw|cup|drink|juice|malt|soda|soft|straw|water
🧋	3	タピオカドリンク|お茶|タピオカ|ドリンク|バブルティー|ミルクティー|飲み物|bubble tea|boba|bubble|food|milk|pearl|tea
🧃	3	紙パック飲料|ジュース|ストロー|ドリンク|飲み物|beverage box|beverage|box|juice|straw|sweet
🧉	3	マテ茶|お茶|ドリンク|飲み物|mate|drink
🧊	3	角氷|冷たい|氷|氷山|ice|cold|cube|iceberg
🥢	3	はし|おはし|箸|chopsticks|hashi|jeotgarak|kuaizi
🍽️	3	ナイフとフォークと皿|お皿|ディナー|ナイフ|フォーク|皿|食器|fork and knife with plate|cooking|dinner|eat|fork|knife|plate
🍴	3	ナイフとフォーク|カトラリー|ごちそう|ディナー|ナイフ|フォーク|レストラン|朝食|食事|食器|fork and knife|breakfast|breaky|cooking|cutlery|delicious|dinner|eat|feed|food|fork|hungry|knife|lunch|restaurant|yum|yummy
🥄	3	スプーン|カトラリー|食器|spoon|eat|tableware
🔪	3	包丁|ナイフ|凶器|料理|調理器具|kitchen knife|chef|cooking|hocho|kitchen|knife|tool|weapon
🫙	3	瓶|ジャー|びん|保存|容器|空|空き瓶|調味料|jar|condiment|container|empty|nothing|sauce|store
🏺	3	壺|かめ|星座|水瓶|amphora|aquarius|cooking|drink|jug|tool|weapon|zodiac
🌍	4	地球（ヨーロッパとアフリカ）|アフリカ|ヨーロッパ|世界|地球|globe showing europe-africa|africa|earth|europe|europe-africa|globe|showing|world
🌎	4	地球（アメリカ大陸）|アメリカ|アメリカ大陸|世界|地球|globe showing americas|americas|earth|globe|showing|world
🌏	4	地球（アジアとオーストラリア）|アジア|オーストラリア|世界|地球|globe showing asia-australia|asia|asia-australia|australia|earth|globe|showing|world
🌐	4	子午線のある地球|インターネット|地球|子午線|globe with meridians|earth|globe|internet|meridians|web|world|worldwide
🗺️	4	世界地図|マップ|世界|地図|world map|map|world
🗾	4	日本地図|地図|日本|map of japan|japan|map
🧭	4	コンパス|オリエンテーリング|方位磁針|方角|磁石|航海|航空|compass|direction|magnetic|navigation|orienteering
🏔️	4	雪山|冠雪|山|雪|snow-capped mountain|cold|mountain|snow|snow-capped
⛰️	4	山|山岳|mountain
🌋	4	火山|噴火|山|溶岩|volcano|eruption|mountain|nature
🗻	4	富士山|山|mount fuji|fuji|mount|mountain|nature
🏕️	4	キャンプ|テント|山|camping
🏖️	4	ビーチパラソル|パラソル|ビーチ|砂浜|beach with umbrella|beach|umbrella
🏜️	4	砂漠|サボテン|サボテンのある砂漠|desert
🏝️	4	無人島|ヤシ|ヤシの木のある島|島|desert island|desert|island
🏞️	4	国立公園|公園|自然|national park|national|park
🏟️	4	競技場|スタジアム|野球場|stadium
🏛️	4	歴史的な建物|古代|史跡|歴史|歴史的建造物|classical building|building|classical
🏗️	4	建設中|クレーン|工事|建築|建設|building construction|building|construction|crane
🧱	4	れんが|しっくい|ブロック|モルタル|壁|煉瓦|粘土|brick|bricks|clay|mortar|wall
🪨	4	岩石|岩|巨岩|石|石材|硬い|重たい|rock|boulder|heavy|solid|stone|tough
🪵	4	丸太|ログ|切り株|木|木材|材木|樹木|wood|log|lumber|timber
🛖	4	わらぶき小屋|バンガロー|ユルト|家|小屋|hut|home|house|roundhouse|shelter|yurt
🏘️	4	住宅街|家|街|複数の家|houses|house
🏚️	4	廃屋|家|建物|空き家|derelict house|derelict|home|house
🏠	4	家|マイホーム|一戸建て|建物|郊外|house|building|country|heart|home|ranch|settle|simple|suburban|suburbia|where
🏡	4	庭付きの家|マイホーム|一戸建て|家|庭|建物|郊外|house with garden|building|country|garden|heart|home|house|ranch|settle|simple|suburban|suburbia|where
🏢	4	オフィスビル|会社|建物|社屋|都会|office building|building|city|cubical|job|office
🏣	4	郵便局|建物|郵便|japanese post office|building|japanese|office|post
🏤	4	西洋の郵便局|建物|郵便|post office|building|european|office|post
🏥	4	病院|医者|建物|薬|hospital|building|doctor|medicine
🏦	4	銀行|建物|bank|building
🏨	4	ホテル|建物|hotel|building
🏩	4	ラブホテル|ホテル|ラブホ|建物|love hotel|building|hotel|love
🏪	4	コンビニ|24時間営業|コンビニエンスストア|店|建物|convenience store|24|building|convenience|hours|store
🏫	4	学校|建物|校舎|school|building
🏬	4	デパート|ビル|店舗|建物|百貨店|department store|building|department|store
🏭	4	工場|ファクトリー|建物|煙突|factory|building
🏯	4	城|建物|日本|日本の城|japanese castle|building|castle|japanese
🏰	4	西洋の城|キャッスル|城|建物|castle|building|european
💒	4	結婚式|チャペル|ロマンス|建物|式場|教会|礼拝堂|結婚|wedding|chapel|hitched|nuptials|romance
🗼	4	東京タワー|タワー|塔|東京|tokyo tower|tokyo|tower
🗽	4	自由の女神|アメリカ|ニューヨーク|彫像|statue of liberty|liberty|new|ny|nyc|statue|york
⛪	4	教会|キリスト教|チャペル|十字架|宗教|建物|礼拝堂|church|bless|chapel|christian|cross|religion
🕌	4	モスク|イスラム教|宗教|建物|mosque|islam|masjid|muslim|religion
🛕	4	ヒンドゥー教の寺院|ヒンズー教|宗教|寺|建物|hindu temple|hindu|temple
🕍	4	シナゴーグ|ユダヤ教|宗教|建物|synagogue|jew|jewish|judaism|religion|temple
⛩️	4	鳥居|宗教|建物|神社|神道|shinto shrine|religion|shinto|shrine
🕋	4	カーバ|イスラム教|メッカ|宗教|建物|聖地|巡礼|kaaba|hajj|islam|muslim|religion|umrah
⛲	4	噴水|fountain
⛺	4	テント|キャンプ|tent|camping
🌁	4	霧の都会|もや|都会|霧|foggy|fog
🌃	4	夜の都会|夜|夜景|星|星空|night with stars|night|star|stars
🏙️	4	高層ビル|ビル群|都会|都市|cityscape|city
🌄	4	山から日の出|ご来光|太陽|日の出|朝|朝日|sunrise over mountains|morning|mountains|over|sun|sunrise
🌅	4	日の出|太陽|日光|朝|朝日|sunrise|morning|nature|sun
🌆	4	夕暮れの都会|ビル|夕方|夕日|夕暮れ|都会|都市|cityscape at dusk|at|building|city|cityscape|dusk|evening|landscape|sun|sunset
🌇	4	夕日|ビル|夕方|夕暮れ|太陽|日暮れ|日没|sunset|building|dusk|sun
🌉	4	夜の橋|夜|夜景|橋|bridge at night|at|bridge|night
♨️	4	温泉マーク|温泉|湯気|hot springs|hot|hotsprings|springs|steaming
🎠	4	メリーゴーランド|メリーゴーラウンド|回転木馬|遊園地|馬|carousel horse|carousel|entertainment|horse
🛝	4	すべり台|公園|遊具|playground slide|amusement|park|play|playground|playing|slide|sliding|theme
🎡	4	観覧車|アミューズメントパーク|遊園地|ferris wheel|amusement|ferris|park|theme|wheel
🎢	4	ジェットコースター|アミューズメントパーク|遊園地|roller coaster|amusement|coaster|park|roller|theme
💈	4	床屋|バーバー|ヘアサロン|理容室|理容院|理髪店|美容室|美容院|barber pole|barber|cut|fresh|haircut|pole|shave
🎪	4	サーカス|サーカス小屋|テント|circus tent|circus|tent
🚂	4	蒸気機関車|sl|乗り物|列車|機関車|汽車|鉄道|locomotive|caboose|engine|railway|steam|train|trains|travel
🚃	4	電車|トロリーバス|乗り物|列車|鉄道|railway car|car|electric|railway|train|tram|travel|trolleybus
🚄	4	新幹線|乗り物|鉄道|電車|高速列車|high-speed train|high-speed|railway|shinkansen|speed|train
🚅	4	0系新幹線|0系|丸い新幹線|乗り物|新幹線|鉄道|電車|高速列車|bullet train|bullet|high-speed|nose|railway|shinkansen|speed|train|travel
🚆	4	電車正面|乗り物|列車|電車|train|arrived|choo|railway
🚇	4	地下鉄|メトロ|乗り物|電車|metro|subway|travel
🚈	4	ライトレール|モノレール|乗り物|鉄道|電車|light rail|arrived|light|monorail|rail|railway
🚉	4	駅|ステーション|乗り物|列車|電車|station|railway|train
🚊	4	路面電車正面|乗り物|路面電車|電車|tram|trolleybus
🚝	4	モノレール|乗り物|電車|monorail|vehicle
🚞	4	登山鉄道|乗り物|登山電車|電車|mountain railway|car|mountain|railway|trip
🚋	4	路面電車|トロリーバス|乗り物|電車|tram car|bus|car|tram|trolley|trolleybus
🚌	4	バス|スクールバス|乗り物|車|bus|school|vehicle
🚍	4	バス正面|バス|乗り物|車|oncoming bus|bus|cars|oncoming
🚎	4	トロリーバス|バス|乗り物|路面電車|trolleybus|bus|tram|trolley
🚐	4	マイクロバス|バス|ミニバス|乗り物|小型バス|車|minibus|bus|drive|van|vehicle
🚑	4	救急車|乗り物|車|ambulance|emergency|vehicle
🚒	4	消防車|乗り物|車|fire engine|engine|fire|truck
🚓	4	パトカー|パトロール|乗り物|警察|police car|5–0|car|cops|patrol|police
🚔	4	パトカー正面|パトカー|乗り物|警察|oncoming police car|car|oncoming|police
🚕	4	タクシー|イエローキャブ|乗り物|車|taxi|cab|cabbie|car|drive|vehicle|yellow
🚖	4	タクシー正面|イエローキャブ|タクシー|乗り物|車|oncoming taxi|cab|cabbie|cars|drove|hail|oncoming|taxi|yellow
🚗	4	自動車|ドライブ|乗り物|乗用車|車|automobile|car|driving|vehicle
🚘	4	自動車正面|ドライブ|乗り物|乗用車|自動車|車|oncoming automobile|automobile|car|cars|drove|oncoming|vehicle
🚙	4	アールブイ車|rv車|suv車|乗り物|乗用車|自動車|車|sport utility vehicle|car|drive|recreational|sport|sportutility|utility|vehicle
🛻	4	軽トラック|トラック|ピックアップトラック|乗り物|自動車|車|配達|pickup truck|automobile|car|flatbed|pick-up|pickup|transportation|truck
🚚	4	トラック|乗り物|車|配送|配達|delivery truck|car|delivery|drive|truck|vehicle
🚛	4	トレーラー|トラック|乗り物|車|articulated lorry|articulated|car|drive|lorry|move|semi|truck|vehicle
🚜	4	トラクター|乗り物|車|tractor|vehicle
🏎️	4	レーシングカー|f1|カーレース|スポーツ|モータースポーツ|レース|乗り物|車|racing car|car|racing|zoom
🏍️	4	オートバイ|オートレース|スポーツ|バイク|モータースポーツ|乗り物|motorcycle|racing
🛵	4	スクーター|オートバイ|バイク|原付|motor scooter|motor|scooter
🦽	4	手動式車椅子|アクセシビリティ|椅子|車椅子|manual wheelchair|accessibility|manual|wheelchair
🦼	4	電動車椅子|アクセシビリティ|椅子|車椅子|motorized wheelchair|accessibility|motorized|wheelchair
🛺	4	三輪タクシー|オートリクシャー|タクシー|トゥクトゥク|リクシャー|auto rickshaw|auto|rickshaw|tuk
🚲	4	自転車|サイクリング|乗り物|bicycle|bike|class|cycle|cycling|cyclist|gang|ride|spin|spinning
🛴	4	キックボード|キックスクーター|キックスケーター|kick scooter|kick|scooter
🛹	4	スケートボード|スケボー|ボード|skateboard|board|skate|skater|wheels
🛼	4	ローラースケート|スケート|スポーツ|roller skate|blades|roller|skate|skates|sport
🚏	4	バス停|バス乗り場|乗り物|停留所|bus stop|bus|busstop|stop
🛣️	4	高速道路|ハイウェイ|道路|高速|motorway|highway|road
🛤️	4	線路|軌道|鉄道|railway track|railway|track|train
🛢️	4	ドラム缶|オイル|石油|oil drum|drum|oil
⛽	4	ガソリンスタンド|ガソリン|ガソリンノズル|給油|fuel pump|diesel|fuel|fuelpump|gas|gasoline|pump|station
🛞	4	ホイール|スポーク|タイヤ|自動車|車|車輪|wheel|car|circle|tire|turn|vehicle
🚨	4	パトランプ|サイレン|パトカー|緊急|警察|赤色灯|police car light|alarm|alert|beacon|car|emergency|light|police|revolving|siren
🚥	4	信号横|交差点|交通|信号|信号機|horizontal traffic light|horizontal|intersection|light|signal|stop|stoplight|traffic
🚦	4	信号縦|交差点|交通|信号|信号機|vertical traffic light|drove|intersection|light|signal|stop|stoplight|traffic|vertical
🛑	4	止まれの標識|ストップ|八角形|標識|止まれ|stop sign|octagonal|sign|stop
🚧	4	工事中|サイン|通行止め|construction|barrier
⚓	4	錨|いかり|碇|船|anchor|ship|tool
🛟	4	救命浮環|ブイ|ライフセーバー|レスキュー|安全|救助|救命具|浮き輪|ring buoy|buoy|float|life|lifesaver|preserver|rescue|ring|safety|save|saver|swim
⛵	4	ヨット|セーリング|乗り物|帆船|船|sailboat|boat|resort|sailing|sea|yacht
🛶	4	カヌー|カヤック|ボート|乗り物|船|canoe|boat
🚤	4	スピードボート|ボート|モーターボート|乗り物|快速艇|船|speedboat|billionaire|boat|lake|luxury|millionaire|summer|travel
🛳️	4	客船|乗り物|旅客船|船|passenger ship|passenger|ship
⛴️	4	フェリー|乗り物|客船|船|ferry|boat|passenger
🛥️	4	モーターボート|ボート|乗り物|船|motor boat|boat|motor|motorboat
🚢	4	船|タイタニック号|乗り物|客船|ship|boat|passenger|travel
✈️	4	飛行機|ジェット機|乗り物|旅行|航空機|airplane|aeroplane|fly|flying|jet|plane|travel
🛩️	4	小型飛行機|乗り物|小型機|航空機|飛行機|small airplane|aeroplane|airplane|plane|small
🛫	4	飛行機離陸|乗り物|航空機|離陸|飛行機|airplane departure|aeroplane|airplane|check-in|departure|departures|plane
🛬	4	飛行機着陸|乗り物|着陸|航空機|飛行機|airplane arrival|aeroplane|airplane|arrival|arrivals|arriving|landing|plane
🪂	4	パラシュート|スカイダイビング|パラグライダー|パラセーリング|ハンググライダー|parachute|hang-glide|parasail|skydive
💺	4	座席|シート|席|椅子|seat|chair
🚁	4	ヘリコプター|ヘリ|乗り物|helicopter|copter|roflcopter|travel|vehicle
🚟	4	懸垂式モノレール|モノレール|乗り物|電車|suspension railway|railway|suspension
🚠	4	ケーブルカー|ゴンドラ|リフト|乗り物|mountain cableway|cable|cableway|gondola|lift|mountain|ski
🚡	4	ロープウェイ|ゴンドラ|乗り物|aerial tramway|aerial|cable|car|gondola|ropeway|tramway
🛰️	4	人工衛星|宇宙|satellite|space
🚀	4	ロケット|乗り物|宇宙|宇宙旅行|発射|rocket|launch|rockets|space|travel
🛸	4	空飛ぶ円盤|ufo|乗り物|宇宙|宇宙人|未確認飛行物体|flying saucer|aliens|extra|flying|saucer|terrestrial
🛎️	4	ベルボーイベル|ベル|ホテル|bellhop bell|bell|bellhop|hotel
🧳	4	スーツケース|キャリーケース|パッキング|旅行|旅行かばん|luggage|bag|packing|roller|suitcase|travel
⌛	4	砂時計|タイマー|時計|砂|hourglass done|done|hourglass|sand|time|timer
⏳	4	砂が落ちている砂時計|タイマー|時計|時間|砂|砂時計|hourglass not done|done|flowing|hourglass|hours|not|sand|timer|waiting|yolo
⌚	4	腕時計|ウォッチ|時計|時間|watch|clock|time
⏰	4	目覚まし時計|アラーム|時計|時間|目覚まし|alarm clock|alarm|clock|hours|hrs|late|time|waiting
⏱️	4	ストップウォッチ|タイム|時計|stopwatch|clock|time
⏲️	4	タイマー|時計|timer clock|clock|timer
🕰️	4	置時計|マンテルクロック|時計|時間|置き時計|mantelpiece clock|clock|mantelpiece|time
🕛	4	12時|12:00|時計|正午|零時|twelve o’clock|12|clock|o’clock|time|twelve
🕧	4	12時半|12:30|12時30分|時計|twelve-thirty|12|30|clock|thirty|time|twelve
🕐	4	1時|1:00|時計|one o’clock|1|clock|o’clock|one|time
🕜	4	1時半|1:30|1時30分|時計|one-thirty|1|30|clock|one|thirty|time
🕑	4	2時|2:00|時計|two o’clock|2|clock|o’clock|time|two
🕝	4	2時半|2:30|2時30分|時計|two-thirty|2|30|clock|thirty|time|two
🕒	4	3時|3:00|時計|three o’clock|3|clock|o’clock|three|time
🕞	4	3時半|3:30|3時30分|時計|three-thirty|3|30|clock|thirty|three|time
🕓	4	4時|4:00|時計|four o’clock|4|clock|four|o’clock|time
🕟	4	4時半|4:30|4時30分|時計|four-thirty|30|4|clock|four|thirty|time
🕔	4	5時|5:00|時計|five o’clock|5|clock|five|o’clock|time
🕠	4	5時半|5:30|5時30分|時計|five-thirty|30|5|clock|five|thirty|time
🕕	4	6時|6:00|時計|six o’clock|6|clock|o’clock|six|time
🕡	4	6時半|6:30|6時30分|時計|six-thirty|30|6|clock|six|thirty
🕖	4	7時|7:00|時計|seven o’clock|0|7|clock|o’clock|seven
🕢	4	7時半|7:30|7時30分|時計|seven-thirty|30|7|clock|seven|thirty
🕗	4	8時|8:00|時計|eight o’clock|8|clock|eight|o’clock|time
🕣	4	8時半|8:30|8時30分|時計|eight-thirty|30|8|clock|eight|thirty|time
🕘	4	9時|9:00|時計|nine o’clock|9|clock|nine|o’clock|time
🕤	4	9時半|9:30|9時30分|時計|nine-thirty|30|9|clock|nine|thirty|time
🕙	4	10時|10:00|時計|ten o’clock|0|10|clock|o’clock|ten
🕥	4	10時半|10:30|10時30分|時計|ten-thirty|10|30|clock|ten|thirty|time
🕚	4	11時|11:00|時計|eleven o’clock|11|clock|eleven|o’clock|time
🕦	4	11時半|11:30|11時30分|時計|eleven-thirty|11|30|clock|eleven|thirty|time
🌑	4	新月|宇宙|月|闇|new moon|dark|moon|new|space
🌒	4	初月|三日月|宇宙|月|waxing crescent moon|crescent|dreams|moon|space|waxing
🌓	4	上弦の月|上弦|半月|宇宙|月|first quarter moon|first|moon|quarter|space
🌔	4	十三夜月|十日夜の月|宇宙|月|waxing gibbous moon|gibbous|moon|space|waxing
🌕	4	満月|宇宙|月|full moon|full|moon|space
🌖	4	寝待月|宇宙|居待月|月|waning gibbous moon|gibbous|moon|space|waning
🌗	4	下弦の月|下弦|半月|宇宙|月|last quarter moon|last|moon|quarter|space
🌘	4	有明月|三日月|宇宙|月|waning crescent moon|crescent|moon|space|waning
🌙	4	三日月|宇宙|月|crescent moon|crescent|moon|ramadan|space
🌚	4	顔のある新月|新月|月|顔|new moon face|face|moon|new|space
🌛	4	顔のある上弦の月|上弦の月|半月|月|顔|first quarter moon face|face|first|moon|quarter|space
🌜	4	顔のある下弦の月|下弦の月|半月|月|顔|last quarter moon face|dreams|face|last|moon|quarter
🌡️	4	温度計|天気|寒暖計|気温|温度|thermometer|weather
☀️	4	太陽|天気|日光|晴れ|sun|bright|rays|space|sunny|weather
🌝	4	顔のある満月|月|満月|顔|full moon face|bright|face|full|moon
🌞	4	顔のある太陽|太陽|顔|sun with face|beach|bright|day|face|heat|shine|sun|sunny|sunshine|weather
🪐	4	環のある惑星|リング|土星|天体|惑星|星|ringed planet|planet|ringed|saturn|saturnine
⭐	4	スター|天文学|星|star|astronomy|medium|stars|white
🌟	4	きらきら星|きらきら|キラキラ|スター|星|輝き|glowing star|glittery|glow|glowing|night|shining|sparkle|star|win
🌠	4	流れ星|スター|夜|宇宙|星|shooting star|falling|night|shooting|space|star
🌌	4	天の川|ミルキーウェイ|夜空|宇宙|星空|銀河|milky way|milky|space|way
☁️	4	雲|天気|曇り|cloud|weather
⛅	4	曇り時々晴れ|天気|太陽|曇り|雲|sun behind cloud|behind|cloud|cloudy|sun|weather
⛈️	4	雷雨|天気|稲妻|雨|雲|雷|雷雲|cloud with lightning and rain|cloud|lightning|rain|thunder|thunderstorm
🌤️	4	晴れ時々曇り|天気|太陽|晴れ|雲|sun behind small cloud|behind|cloud|sun|weather
🌥️	4	曇り一時晴れ|天気|太陽|曇り|雲|sun behind large cloud|behind|cloud|sun|weather
🌦️	4	雨時々晴れ|天気|天気雨|太陽|曇り|雲|sun behind rain cloud|behind|cloud|rain|sun|weather
🌧️	4	雨雲|天気|雨|雲|cloud with rain|cloud|rain|weather
🌨️	4	雪雲|天気|雪|雲|cloud with snow|cloud|cold|snow|weather
🌩️	4	雷雲|天気|稲妻|雲|雷|cloud with lightning|cloud|lightning|weather
🌪️	4	竜巻|トルネード|天気|突風|tornado|cloud|weather|whirlwind
🌫️	4	霧|天気|曇り|雲|fog|cloud|weather
🌬️	4	顔のある風|雲|顔|風|wind face|blow|cloud|face|wind
🌀	4	渦巻き|サイクロン|ハリケーン|めまい|もやもや|台風|天気|cyclone|dizzy|hurricane|twister|typhoon|weather
🌈	4	虹|glbt|glbtq|lgbt|lgbtq|ゲイ|トランスジェンダー|バイセクシュアル|レインボー|レズビアン|天気|rainbow|gay|genderqueer|lesbian|lgbtqia|nature|pride|queer|rain|trans|transgender|weather
🌂	4	閉じた傘|傘|天気|持ち物|雨|closed umbrella|closed|clothing|rain|umbrella
☂️	4	傘|天気|持ち物|雨|umbrella|clothing|rain
☔	4	傘と雨|傘|天気|持ち物|雨|雨粒|umbrella with rain drops|clothing|drop|drops|rain|umbrella|weather
⛱️	4	パラソル|ビーチ|傘|日傘|砂浜|雨|umbrella on ground|ground|rain|sun|umbrella
⚡	4	高電圧|危険|稲妻|雷|電気|high voltage|danger|electric|electricity|high|lightning|nature|thunder|thunderbolt|voltage|zap
❄️	4	雪の結晶|天気|結晶|雪|snowflake|cold|snow|weather
☃️	4	雪だるまと雪|雪|雪だるま|snowman|cold|man|snow
⛄	4	雪だるま|雪|snowman without snow|cold|man|snow|snowman
☄️	4	彗星|すい星|宇宙|comet|space
🔥	4	火|ファイアー|ファイヤー|炎|熱い|燃える|fire|af|burn|flame|hot|lit|litaf|tool
💧	4	水滴|しずく|たらーっ|水|汗|涙|droplet|cold|comic|drop|nature|sad|sweat|tear|water|weather
🌊	4	波|天気|波浪|海|自然|water wave|nature|ocean|surf|surfer|surfing|water|wave
🎃	5	ハロウィンかぼちゃ|かぼちゃ|ハロウィーン|ハロウィン|jack-o-lantern|celebration|halloween|jack|lantern|pumpkin
🎄	5	クリスマスツリー|クリスマス|クリスマスイブ|ツリー|christmas tree|celebration|christmas|tree
🎆	5	打ち上げ花火|お祭り|どーん|花火|花火大会|fireworks|boom|celebration|entertainment|yolo
🎇	5	線香花火|お祭り|火花|花火|sparkler|boom|celebration|fireworks|sparkle
🧨	5	爆竹|ダイナマイト|火花|火薬|爆発|爆発物|爆薬|花火|firecracker|dynamite|explosive|fire|fireworks|light|pop|popping|spark
✨	5	きらきら|キラキラ|スター|ぴかぴか|星|sparkles|*|magic|sparkle|star
🎈	5	風船|バルーン|balloon|birthday|celebrate|celebration
🎉	5	クラッカー|お祝い|パーティー|パーティークラッカー|誕生日|party popper|awesome|birthday|celebrate|celebration|excited|hooray|party|popper|tada|woohoo
🎊	5	くす玉|お祝い|パーティー|紙吹雪|confetti ball|ball|celebrate|celebration|confetti|party|woohoo
🎋	5	七夕|和|日本|短冊|笹飾り|tanabata tree|banner|celebration|japanese|tanabata|tree
🎍	5	門松|お祝い|和|日本|正月|竹|pine decoration|bamboo|celebration|decoration|japanese|pine|plant
🎎	5	ひな祭り|おだいり様|おひな様|お祝い|ひな人形|人形|和|日本|japanese dolls|celebration|doll|dolls|festival|japanese
🎏	5	こいのぼり|お祝い|子供の日|carp streamer|carp|celebration|streamer
🎐	5	風鈴|夏|鈴|風|wind chime|bell|celebration|chime|wind
🎑	5	月見|ススキ|十五夜|団子|月|moon viewing ceremony|celebration|ceremony|moon|viewing
🧧	5	赤い封筒|お年玉|お金|ご祝儀|幸運|紅包|red envelope|envelope|gift|good|hóngbāo|lai|luck|money|red|see
🎀	5	リボン|お祝い|ribbon|celebration
🎁	5	プレゼント|お祝い|ギフト|リボン|箱|wrapped gift|birthday|bow|box|celebration|christmas|gift|present|surprise|wrapped
🎗️	5	リマインダーリボン|お祝い|リボン|リマインダー|reminder ribbon|celebration|reminder|ribbon
🎟️	5	入場券|チケット|入場パス|半券|admission tickets|admission|ticket|tickets
🎫	5	チケット|入場券|切符|半券|ticket|admission|stub
🎖️	5	勲章|お祝い|メダル|表彰|military medal|award|celebration|medal|military
🏆	5	トロフィー|チャンピオン|優勝|優勝カップ|表彰|賞杯|trophy|champion|champs|prize|slay|sport|victory|win|winning
🏅	5	メダル|スポーツ|優勝|表彰|金|金メダル|sports medal|award|gold|medal|sports|winner
🥇	5	金メダル|1位|ゴールド|メダル|優勝|金|1st place medal|1st|first|gold|medal|place
🥈	5	銀メダル|2位|シルバー|メダル|銀|2nd place medal|2nd|medal|place|second|silver
🥉	5	銅メダル|3位|ブロンズ|メダル|銅|3rd place medal|3rd|bronze|medal|place|third
⚽	5	サッカー|サッカーボール|スポーツ|ボール|soccer ball|ball|football|futbol|soccer|sport
⚾	5	野球|スポーツ|ボール|baseball|ball|sport
🥎	5	ソフトボール|スポーツ|ボール|下手投げ|softball|ball|glove|sports|underarm
🏀	5	バスケットボール|スポーツ|バスケ|ボール|basketball|ball|hoop|sport
🏐	5	バレーボール|スポーツ|バレー|ボール|volleyball|ball|game
🏈	5	アメフト|アメリカンフットボール|スポーツ|フットボール|ボール|american football|american|ball|bowl|football|sport|super
🏉	5	ラグビー|スポーツ|ボール|ラグビーボール|rugby football|ball|football|rugby|sport
🎾	5	テニス|スポーツ|テニスボール|ボール|ラケット|tennis|ball|racquet|sport
🥏	5	フリスビー|アルティメット|フライングディスク|円盤|flying disc|disc|flying|ultimate
🎳	5	ボウリング|ストライク|スポーツ|ボール|球|bowling|ball|game|sport|strike
🏏	5	クリケット|スポーツ|バット|ボール|cricket game|ball|bat|cricket|game
🏑	5	ホッケー|スティック|スポーツ|フィールドホッケー|ボール|field hockey|ball|field|game|hockey|stick
🏒	5	アイスホッケー|スティック|スポーツ|パック|ホッケー|ice hockey|game|hockey|ice|puck|stick
🥍	5	ラクロス|ゴール|スティック|スポーツ|ボール|lacrosse|ball|goal|sports|stick
🏓	5	卓球|スポーツ|ピンポン|ボール|ラケット|ping pong|ball|bat|game|paddle|ping|pingpong|pong|table|tennis
🏸	5	バドミントン|シャトル|スポーツ|ラケット|badminton|birdie|game|racquet|shuttlecock
🥊	5	ボクシング|グローブ|スポーツ|ボクシンググローブ|boxing glove|boxing|glove
🥋	5	武道|スポーツ|テコンドー|柔道|格闘技|空手|道着|martial arts uniform|arts|judo|karate|martial|taekwondo|uniform
🥅	5	ゴールネット|ゴール|スポーツ|ネット|goal net|goal|net
⛳	5	ゴルフ|スポーツ|フラグ|フラッグ|ホール|旗|flag in hole|flag|golf|hole|sport
⛸️	5	アイススケート|スケート|スポーツ|フィギュアスケート|ice skate|ice|skate|skating
🎣	5	釣り|スポーツ|フィッシング|釣り竿|魚|fishing pole|entertainment|fish|fishing|pole|sport
🤿	5	ダイビング マスク|シュノーケリング|スキューバ|スポーツ|ダイビング|マスク|diving mask|diving|mask|scuba|snorkeling
🎽	5	長距離走|スポーツ|たすき|ランニング|ランニングシャツ|駅伝|running shirt|athletics|running|sash|shirt
🎿	5	スキー|ストック|スポーツ|板|雪|skis|ski|snow|sport
🛷	5	そり競技|スケルトン|スポーツ|そり|トボガン|ボブスレー|リュージュ|sled|luge|sledge|sleigh|snow|toboggan
🥌	5	カーリング|ゲーム|ストーン|スポーツ|試合|curling stone|curling|game|rock|stone
🎯	5	的|スポーツ|ダーツ|ブルズアイ|当たり|的中|bullseye|bull|dart|direct|entertainment|game|hit|target
🪀	5	ヨーヨー|おもちゃ|上下|回転|玩具|yo-yo|fluctuate|toy
🪁	5	たこ|おもちゃ|カイト|凧揚げ|玩具|kite|fly|soar
🔫	5	水鉄砲|ピストル|リボルバー|拳銃|武器|銃|water pistol|gun|handgun|pistol|revolver|tool|water|weapon
🎱	5	ビリヤード|エイトボール|スポーツ|ボール|玉突き|pool 8 ball|8|8ball|ball|billiard|eight|game|pool
🔮	5	水晶玉|クリスタル|占い|水晶|水晶球|玉|運命|魔法|crystal ball|ball|crystal|fairy|fairytale|fantasy|fortune|future|magic|tale|tool
🪄	5	魔法の杖|つえ|マジック|手品|杖|魔法|魔術|魔術師|magic wand|magic|magician|wand|witch|wizard
🎮	5	テレビゲーム|ゲーム|コントローラ|ビデオゲーム|video game|controller|entertainment|game|video
🕹️	5	ジョイスティック|ゲーム|コントローラ|スティック|テレビゲーム|ビデオゲーム|joystick|game|video|videogame
🎰	5	スロットマシン|カジノ|ギャンブル|ゲーム|スリーセブン|スロット|slot machine|casino|gamble|gambling|game|machine|slot|slots
🎲	5	サイコロ|ゲーム|ダイス|game die|dice|die|entertainment|game
🧩	5	ジグソーパズル|パズル|ピース|組み合わせ|鍵|puzzle piece|clue|interlocking|jigsaw|piece|puzzle
🧸	5	テディベア|おもちゃ|くま|ぬいぐるみ|玩具|teddy bear|bear|plaything|plush|stuffed|teddy|toy
🪅	5	ピニャータ|お祝い|パーティー|メキシコ|人形|誕生日|piñata|candy|celebrate|celebration|cinco|de|festive|mayo|party|pinada|pinata
🪩	5	ミラーボール|キラキラ|ダンス|ディスコ|パーティー|mirror ball|ball|dance|disco|glitter|mirror|party
🪆	5	マトリョーシカ|ロシア|人形|入れ子|nesting dolls|babooshka|baboushka|babushka|doll|dolls|matryoshka|nesting|russia
♠️	5	スペード|カード|トランプ|spade suit|card|game|spade|suit
♥️	5	ハート|カード|トランプ|heart suit|card|emotion|game|heart|hearts|suit
♦️	5	ダイヤ|カード|ダイア|トランプ|diamond suit|card|diamond|game|suit
♣️	5	クラブ|カード|クローバー|トランプ|club suit|card|club|clubs|game|suit
♟️	5	チェスの駒|チェス|捨て駒|駒|chess pawn|chess|dupe|expendable|pawn
🃏	5	ジョーカー|カード|ジジ|トランプ|ババ|joker|card|game|wildcard
🀄	5	麻雀|ゲーム|中|牌|麻雀牌|mahjong red dragon|dragon|game|mahjong|red
🎴	5	花札|カード|ゲーム|flower playing cards|card|cards|flower|game|japanese|playing
🎭	5	舞台芸術|仮面|俳優|劇場|女優|役者|演劇|演芸|芸術|performing arts|actor|actress|art|arts|entertainment|mask|performing|theater|theatre|thespian
🖼️	5	絵画|アート|絵|美術館|芸術|額|framed picture|art|frame|framed|museum|painting|picture
🎨	5	絵の具パレット|パレット|画家|絵|絵画|美術館|芸術|artist palette|art|artist|artsy|arty|colorful|creative|entertainment|museum|painter|painting|palette
🧵	5	糸|ソーイング|ひも|糸巻き|縫い針|裁縫|thread|needle|sewing|spool|string
🪡	5	縫い針|ステッチ|刺繍|糸|縫う|裁縫|針|sewing needle|embroidery|needle|sew|sewing|stitches|sutures|tailoring|thread
🧶	5	毛糸|かぎ針|ニット|毛糸玉|編み物|yarn|ball|crochet|knit
🪢	5	結び目|ねじれ|ノット|ひも|ロープ|綱|knot|cord|rope|tangled|tie|twine|twist
👓	6	メガネ|眼鏡|glasses|clothing|eye|eyeglasses|eyewear
🕶️	6	サングラス|メガネ|眼鏡|sunglasses|dark|eye|eyewear|glasses
🥽	6	ゴーグル|スキューバ|ダイビング|保護メガネ|水泳|溶接|goggles|dive|eye|protection|scuba|swimming|welding
🥼	6	白衣|上着|医者|実験|服|科学者|lab coat|clothes|coat|doctor|dr|experiment|jacket|lab|scientist|white
🦺	6	安全ベスト|チョッキ|ベスト|反射|安全|工事|緊急|safety vest|emergency|safety|vest
👔	6	ネクタイ|シャツ|ワイシャツ|会社員|服|necktie|clothing|employed|serious|shirt|tie
👕	6	tシャツ|シャツ|服|t-shirt|blue|casual|clothes|clothing|collar|dressed|shirt|shopping|tshirt|weekend
👖	6	ジーンズ|カジュアル|ズボン|デニム|パンツ|ブルージーンズ|服|jeans|blue|casual|clothes|clothing|denim|dressed|pants|shopping|trousers|weekend
🧣	6	マフラー|スカーフ|厚着|寒い|襟巻き|首巻き|scarf|bundle|cold|neck|up
🧤	6	手袋|手|gloves|hand
🧥	6	コート|アウター|オーバー|ジャケット|厚着|寒い|服|coat|brr|bundle|cold|jacket|up
🧦	6	ソックス|靴下|socks|stocking
👗	6	ワンピース|ドレス|服|dress|clothes|clothing|dressed|fancy|shopping
👘	6	着物|和服|服|kimono|clothing|comfortable
🥻	6	サリー|ドレス|服|民族衣装|sari|clothing|dress
🩱	6	ワンピースの水着|スイムウェア|水着|one-piece swimsuit|bathing|one-piece|suit|swimsuit
🩲	6	ブリーフ|スイムウェア|パンツ|下着|水着|briefs|bathing|one-piece|suit|swimsuit|underwear
🩳	6	ショーツ|スイムウェア|パンツ|下着|水着|shorts|bathing|pants|suit|swimsuit|underwear
👙	6	ビキニ|ビーチ|プール|水着|bikini|bathing|beach|clothing|pool|suit|swim
👚	6	婦人服|シャツ|ブラウス|服|woman’s clothes|blouse|clothes|clothing|collar|dress|dressed|lady|shirt|shopping|woman|woman’s
🪭	6	扇子|あおぐ|パタパタ|団扇|夏|扇|涼む|風|folding hand fan|clack|clap|cool|cooling|dance|fan|flirt|flutter|folding|hand|hot|shy
👛	6	がま口|小銭入れ|財布|purse|clothes|clothing|coin|dress|fancy|handbag|shopping
👜	6	ハンドバッグ|かばん|ショッピング|バッグ|handbag|bag|clothes|clothing|dress|lady|purse|shopping
👝	6	ポーチ|クラッチバッグ|バッグ|ハンドバッグ|clutch bag|bag|clothes|clothing|clutch|dress|handbag|pouch|purse
🛍️	6	紙袋|ショッピングバッグ|バッグ|紙手提げ|shopping bags|bag|bags|hotel|shopping
🎒	6	バックパック|ランドセル|リュックサック|学校|小学校|小学生|backpack|backpacking|bag|bookbag|education|rucksack|satchel|school
🩴	6	ビーチサンダル|ゴムぞうり|サンダル|トングサンダル|ビーサン|靴|thong sandal|beach|flip|flop|sandal|sandals|shoe|thong|thongs|zōri
👞	6	紳士靴|ローファー|革靴|靴|man’s shoe|brown|clothes|clothing|feet|foot|kick|man|man’s|shoe|shoes|shopping
👟	6	スニーカー|ジョギングシューズ|テニスシューズ|靴|running shoe|athletic|clothes|clothing|fast|kick|running|shoe|shoes|shopping|sneaker|tennis
🥾	6	ハイキングシューズ|アウトドア|キャンプ|ハイキング|ブーツ|登山靴|茶色い靴|靴|hiking boot|backpacking|boot|brown|camping|hiking|outdoors|shoe
🥿	6	フラットシューズ|スリッポン|バレエシューズ|ぺたんこ靴|flat shoe|ballet|comfy|flat|flats|shoe|slip-on|slipper
👠	6	ハイヒール|ピンヒール|婦人靴|靴|high-heeled shoe|clothes|clothing|dress|fashion|heel|heels|high-heeled|shoe|shoes|shopping|stiletto|woman
👡	6	サンダル|婦人靴|靴|woman’s sandal|clothing|sandal|shoe|woman|woman’s
🩰	6	トウシューズ|シューズ|ダンス|バレエ|バレエシューズ|靴|ballet shoes|ballet|dance|shoes
👢	6	ブーツ|ロングブーツ|婦人靴|靴|woman’s boot|boot|clothes|clothing|dress|shoe|shoes|shopping|woman|woman’s
🪮	6	アフロコーム|アフロ|くし|コーム|髪|hair pick|afro|comb|groom|hair|pick
👑	6	王冠|クラウン|冠|王族|crown|clothing|family|king|medieval|queen|royal|royalty|win
👒	6	婦人帽子|帽子|麦わら|麦わら帽子|woman’s hat|clothes|clothing|garden|hat|hats|party|woman|woman’s
🎩	6	シルクハット|山高帽|帽子|top hat|clothes|clothing|fancy|formal|hat|magic|top|tophat
🎓	6	角帽|卒業|学者|帽子|graduation cap|cap|celebration|clothing|education|graduation|hat|scholar
🧢	6	キャップ|帽子|野球帽|billed cap|baseball|bent|billed|cap|dad|hat
🪖	6	軍用ヘルメット|ヘルメット|ミリタリー|兵士|戦闘|軍隊|military helmet|army|helmet|military|soldier|war|warrior
⛑️	6	白十字ヘルメット|ヘルメット|帽子|救助隊|救急|白十字|rescue worker’s helmet|aid|cross|face|hat|helmet|rescue|worker’s
📿	6	数珠|ネックレス|ロザリオ|宗教|念珠|祈る|prayer beads|beads|clothing|necklace|prayer|religion
💄	6	口紅|コスメ|メーク|リップ|リップスティック|化粧|化粧品|lipstick|cosmetics|date|makeup
💍	6	指輪|ジュエリー|ダイア|ダイヤ|ダイヤモンド|リング|婚約指輪|ring|diamond|engaged|engagement|married|romance|shiny|sparkling|wedding
💎	6	宝石|ジュエリー|ダイア|ダイヤ|ダイヤモンド|gem stone|diamond|engagement|gem|jewel|money|romance|stone|wedding
🔇	6	スピーカー消音|スピーカー|ミュート|消音|無音|音無し|muted speaker|mute|muted|quiet|silent|sound|speaker
🔈	6	スピーカー|ボリューム|音量|speaker low volume|low|soft|sound|speaker|volume
🔉	6	スピーカー音量小|スピーカー|ボリューム|音量|音量小|speaker medium volume|medium|sound|speaker|volume
🔊	6	スピーカー音量大|スピーカー|ボリューム|大音量|音量|音量大|speaker high volume|high|loud|music|sound|speaker|volume
📢	6	拡声器|スピーカー|メガホン|放送|loudspeaker|address|communication|loud|public|sound
📣	6	メガホン|スピーカー|応援|拡声器|megaphone|cheering|sound
📯	6	郵便ラッパ|ラッパ|郵便|postal horn|horn|post|postal
🔔	6	ベル|教会|鈴|鐘|bell|break|church|sound
🔕	6	ベル消音|ミュート|消音|鈴|鐘|音無し|bell with slash|bell|forbidden|mute|no|not|prohibited|quiet|silent|slash|sound
🎼	6	楽譜|スコア|ト音記号|五線譜|音楽|musical score|music|musical|note|score
🎵	6	音符|8分音符|連符|音楽|musical note|music|musical|note|sound
🎶	6	複数の音符|8分音符|音楽|音符|musical notes|music|musical|note|notes|sound
🎙️	6	スタジオマイク|マイク|音楽|studio microphone|mic|microphone|music|studio
🎚️	6	レベルスライダー|スライダー|レベル|音楽|level slider|level|music|slider
🎛️	6	コントロールつまみ|コントロール|つまみ|ノブ|調整|音楽|control knobs|control|knobs|music
🎤	6	マイク|カラオケ|歌|音楽|microphone|karaoke|mic|music|sing|sound
🎧	6	ヘッドホン|イヤホン|ヘッドフォン|音楽|headphone|earbud|sound
📻	6	ラジオ|受信機|放送|音楽|radio|entertainment|tbt|video
🎷	6	サックス|サキソフォン|サクソフォン|楽器|音楽|saxophone|instrument|music|sax
🎺	6	トランペット|ラッパ|楽器|音楽|trumpet|instrument|music
🪗	6	アコーディオン|じゃばら|楽器|音楽|accordion|box|concertina|instrument|music|squeeze|squeezebox
🎸	6	ギター|エレキギター|楽器|音楽|guitar|instrument|music|strat
🎹	6	鍵盤|キーボード|ピアノ|楽器|音楽|musical keyboard|instrument|keyboard|music|musical|piano
🎻	6	バイオリン|ヴィオラ|ストラディバリウス|楽器|音楽|violin|instrument|music
🪕	6	バンジョー|弦楽器|楽器|音楽|banjo|music|stringed
🥁	6	ドラム|ドラムスティック|太鼓|楽器|音楽|drum|drumsticks|music
🪘	6	コンガ|ドラム|ビート|リズム|太鼓|楽器|音楽|long drum|beat|conga|drum|instrument|long|rhythm
🪇	6	マラカス|シェイカー|ダンス|パーティー|ラトル|リズム|打楽器|振る|楽器|音楽|maracas|cha|dance|instrument|music|party|percussion|rattle|shake|shaker
🪈	6	笛|オーケストラ|バンド|ピッコロ|フルート|フルート奏者|リコーダー|楽器|横笛|管楽器|音楽|flute|band|fife|flautist|instrument|marching|music|orchestra|piccolo|pipe|recorder|woodwind
📱	6	携帯電話|スマートフォン|スマホ|モバイル|携帯|mobile phone|cell|communication|mobile|phone|telephone
📲	6	着信中|スマートフォン|スマホ|モバイル|受信|携帯受信|携帯電話|電話|mobile phone with arrow|arrow|build|call|cell|communication|mobile|phone|receive|telephone
"""

private const val UNICODE_EMOJI_DATA_3: String = """
☎️	6	固定電話|ボタン式電話|親機|電話|黒電話|telephone|phone
📞	6	受話器|子機|電話|telephone receiver|communication|phone|receiver|telephone|voip
📟	6	ポケベル|ページャー|ポケットベル|pager|communication
📠	6	ファックス|fax|複合機|電話|fax machine|communication|machine
🔋	6	電池|バッテリー|乾電池|battery
🪫	6	バッテリー残量低下|バッテリー|充電不足|電池切れ寸前|電池残量低下|low battery|battery|drained|electronic|energy|low|power
🔌	6	コンセント|プラグ|電気|電源|electric plug|electric|electricity|plug
💻	6	ノートパソコン|pc|コンピューター|パソコン|ラップトップ|laptop|computer|office|personal
🖥️	6	デスクトップパソコン|pc|コンピューター|デスクトップ|パソコン|モニター|desktop computer|computer|desktop|monitor
🖨️	6	プリンタ|コピー機|コンピューター|プリンター|印刷機|複合機|printer|computer
⌨️	6	キーボード|pc|コンピューター|パソコン|keyboard|computer
🖱️	6	マウス|pc|コンピューター|パソコン|computer mouse|computer|mouse
🖲️	6	トラックボール|pc|コンピューター|パソコン|trackball|computer
💽	6	md|mo|コンピューター|ミニディスク|computer disk|computer|disk|minidisk|optical
💾	6	フロッピー|fd|コンピューター|ディスク|フロッピーディスク|floppy disk|computer|disk|floppy
💿	6	cd|blu-ray|dvd|コンピューター|ディスク|ブルーレイ|光ディスク|optical disk|computer|disk|optical
📀	6	dvd|blu-ray|cd|ディスク|ブルーレイ|computer|disk|optical
🧮	6	そろばん|アバカス|算盤|計算|abacus|calculation|calculator
🎥	6	映画カメラ|カメラ|シネマ|ハリウッド|フィルム|ムービー|撮影|映画|movie camera|bollywood|camera|cinema|film|hollywood|movie|record
🎞️	6	映画フィルム|シネマ|フィルム|フレーム|ムービー|映画|film frames|cinema|film|frames|movie
📽️	6	映写機|ビデオ|フィルム|プロジェクター|ムービー|映画|film projector|cinema|film|movie|projector|video
🎬	6	カチンコ|映画|clapper board|action|board|clapper|movie
📺	6	テレビ|tv|ビデオ|television|video
📷	6	カメラ|写真|camera|photo|selfie|snap|tbt|trip|video
📸	6	フラッシュを焚いているカメラ|カメラ|カメラフラッシュ|フラッシュ|写真|camera with flash|camera|flash|video
📹	6	ビデオカメラ|カメラ|ビデオ|ムービー|動画|録画|video camera|camcorder|camera|tbt|video
📼	6	ビデオテープ|vhs|テープ|ビデオ|ビデオカセット|動画|videocassette|old|school|tape|vcr|video
🔍	6	虫眼鏡左|ルーペ|拡大鏡|検索|科学|虫めがね|magnifying glass tilted left|glass|lab|left|left-pointing|magnifying|science|search|tilted|tool
🔎	6	虫眼鏡右|ルーペ|拡大鏡|検索|科学|虫めがね|magnifying glass tilted right|contact|glass|lab|magnifying|right|right-pointing|science|search|tilted|tool
🕯️	6	ろうそく|キャンドル|ロウソク|明かり|candle|light
💡	6	電球|ぴこーん|ひらめき|マンガ|明かり|light bulb|bulb|comic|electric|idea|light
🔦	6	懐中電灯|ライト|明かり|flashlight|electric|light|tool|torch
🏮	6	赤ちょうちん|ちょうちん|居酒屋|飲み屋|red paper lantern|bar|lantern|light|paper|red|restaurant
🪔	6	ディヤランプ|オイル|ランプ|明かり|diya lamp|diya|lamp|light|oil
📔	6	表紙付きノート|ノート|学校|文具|文房具|本|notebook with decorative cover|book|cover|decorated|decorative|education|notebook|school|writing
📕	6	閉じた本|書籍|本|赤い本|closed book|book|closed|education
📖	6	開いた本|図書館|小説|書籍|本|知識|読書|open book|book|education|fantasy|knowledge|library|novels|open|reading
📗	6	緑の本|図書館|書籍|本|読書|green book|book|education|fantasy|green|library|reading
📘	6	青の本|図書館|書籍|本|読書|青い本|blue book|blue|book|education|fantasy|library|reading
📙	6	オレンジの本|図書館|書籍|本|読書|orange book|book|education|fantasy|library|orange|reading
📚	6	本の山|勉強|学校|小説|書籍|本|読書|books|book|education|fantasy|knowledge|library|novels|reading|school|study
📓	6	ノート|手帳|文具|文房具|notebook
📒	6	リングノート|ノート|台帳|文具|文房具|ledger|notebook
📃	6	巻きページ|カール|ドキュメント|文書|書類|page with curl|curl|document|page|paper
📜	6	巻き物|文書|書類|紙|scroll|paper
📄	6	ページ|ドキュメント|文書|書類|page facing up|document|facing|page|paper|up
📰	6	新聞|ニュース|新聞紙|newspaper|communication|news|paper
🗞️	6	丸めた新聞|ニュース|新聞|新聞紙|rolled-up newspaper|news|newspaper|paper|rolled|rolled-up
📑	6	ページに付箋|ふせん|付箋|文具|文房具|bookmark tabs|bookmark|mark|marker|tabs
🔖	6	しおり|タグ|ブックマーク|付箋|bookmark|mark
🏷️	6	荷札|タグ|ラベル|label|tag
🪙	6	コイン|お金|小銭|硬貨|金貨|銀貨|coin|dollar|euro|gold|metal|money|rich|silver|treasure
💰	6	ドル袋|お金|キャッシュ|ドル|現金|賞金|賭け金|金持ち|金袋|銀行|money bag|bag|bank|bet|billion|cash|cost|dollar|gold|million|money|moneybag|paid|paying|pot|rich|win
💴	6	円札|お札|お金|円|札束|紙幣|通貨|yen banknote|bank|banknote|bill|currency|money|note|yen
💵	6	ドル札|お札|お金|ドル|札束|紙幣|通貨|dollar banknote|bank|banknote|bill|currency|dollar|money|note
💶	6	ユーロ札|お札|お金|ユーロ|札束|紙幣|通貨|euro banknote|100|bank|banknote|bill|currency|euro|money|note|rich
💷	6	ポンド札|お札|お金|ポンド|札束|現金|紙幣|通貨|pound banknote|bank|banknote|bill|billion|cash|currency|money|note|pound|pounds
💸	6	羽が生えたお金|お札|お金|ドル|支払い|札束|紙幣|money with wings|bank|banknote|bill|billion|cash|dollar|fly|million|money|note|pay|wings
💳	6	クレジットカード|お金|カード|キャッシュカード|支払い|銀行|credit card|bank|card|cash|charge|credit|money|pay
🧾	6	レシート|会計|簿記|請求書|領収書|receipt|accounting|bookkeeping|evidence|invoice|proof
💹	6	為替|グラフ|チャート|円|円高|市場|株|株価|chart increasing with yen|bank|chart|currency|graph|growth|increasing|market|money|rise|trend|upward|yen
✉️	6	封筒|eメール|メール|手紙|郵便|電子メール|envelope|e-mail|email|letter
📧	6	eメール|メール|封筒|手紙|郵便|電子メール|e-mail|email|letter|mail
📨	6	メール受信中|eメール|受信中|受信箱|封筒|手紙|電子メール|incoming envelope|delivering|e-mail|email|envelope|incoming|letter|mail|receive|sent
📩	6	メール受信|eメール|メール送信|受信|封筒と矢印|送信|電子メール|envelope with arrow|arrow|communication|down|e-mail|email|envelope|letter|mail|outgoing|send|sent
📤	6	送信トレイ|eメール|トレイ|箱|送信|送信箱|電子メール|outbox tray|box|email|letter|mail|outbox|sent|tray
📥	6	受信トレイ|eメール|トレイ|受信|受信なし|受信箱|箱|電子メール|inbox tray|box|email|inbox|letter|mail|receive|tray|zero
📦	6	荷物|宅急便|宅配便|小包|段ボール|箱|配送|配達|package|box|communication|delivery|parcel|shipping
📫	6	閉じた郵便受け（手紙あり）|ポスト|手紙あり|郵便受け|郵便箱|閉じた|closed mailbox with raised flag|closed|communication|flag|mail|mailbox|postbox|raised
📪	6	閉じた郵便受け（手紙なし）|ポスト|手紙なし|郵便受け|郵便箱|閉じた|closed mailbox with lowered flag|closed|flag|lowered|mail|mailbox|postbox
📬	6	開いた郵便受け（手紙あり）|ポスト|手紙あり|郵便受け|郵便箱|開いた|open mailbox with raised flag|flag|mail|mailbox|open|postbox|raised
📭	6	開いた郵便受け（手紙なし）|ポスト|手紙なし|郵便受け|郵便箱|開いた|open mailbox with lowered flag|flag|lowered|mail|mailbox|open|postbox
📮	6	郵便ポスト|ポスト|手紙|郵便|郵便箱|postbox|mail|mailbox
🗳️	6	投票箱|投票|選挙|ballot box with ballot|ballot|box
✏️	6	鉛筆|文具|文房具|pencil
✒️	6	ペン先|万年筆|文具|文房具|black nib|black|nib|pen
🖋️	6	万年筆|ペン|文具|文房具|fountain pen|fountain|pen
🖊️	6	ペン|ボールペン|文具|文房具|pen|ballpoint
🖌️	6	絵筆|文具|文房具|絵の具|paintbrush|painting
🖍️	6	クレヨン|文具|文房具|crayon
📝	6	鉛筆とメモ|メモ|文具|文房具|紙と鉛筆|鉛筆|memo|communication|media|notes|pencil
💼	6	ブリーフケース|かばん|鞄|briefcase|office
📁	6	フォルダー|ファイル|文具|文房具|file folder|file|folder
📂	6	開いたフォルダー|ファイル|フォルダー|文具|文房具|開く|open file folder|file|folder|open
🗂️	6	カードフォルダー|インデックス|フォルダー|文具|文房具|card index dividers|card|dividers|index
📅	6	カレンダー|スケジュール|予定|日付|calendar|date
📆	6	日めくりカレンダー|カレンダー|スケジュール|予定|日付|tear-off calendar|calendar|tear-off
🗒️	6	メモ帳|ノート|リングメモ|文具|文房具|spiral notepad|note|notepad|pad|spiral
🗓️	6	月めくりカレンダー|カレンダー|スケジュール|予定|日付|spiral calendar|calendar|pad|spiral
📇	6	カードインデックス|名刺ホルダー|文具|文房具|card index|card|index|old|rolodex|school
📈	6	グラフ上向き|グラフ|チャート|データ|上向き|上昇|右肩上がり|成長|折れ線グラフ|chart increasing|chart|data|graph|growth|increasing|right|trend|up|upward
📉	6	グラフ下向き|チャート|データ|下向き|下降|右肩下がり|折れ線グラフ|chart decreasing|chart|data|decreasing|down|downward|graph|negative|trend
📊	6	棒グラフ|グラフ|チャート|データ|bar chart|bar|chart|data|graph
📋	6	クリップボード|クリップ|メモ|文具|文房具|clipboard|do|list|notes
📌	6	押しピン|文具|文房具|画鋲|pushpin|collage|pin
📍	6	丸い押しピン|ピン|プッシュピン|地図|押しピン|文具|文房具|画びょう|round pushpin|location|map|pin|pushpin|round
📎	6	クリップ|ゼムクリップ|ペーパークリップ|文具|文房具|紙クリップ|paperclip
🖇️	6	つながったクリップ|クリップ|ペーパークリップ|文具|文房具|紙クリップ|linked paperclips|link|linked|paperclip|paperclips
📏	6	定規|ものさし|文具|文房具|直定規|straight ruler|angle|edge|math|ruler|straight|straightedge
📐	6	三角定規|定規|文具|文房具|triangular ruler|angle|math|rule|ruler|set|slide|triangle|triangular
✂️	6	はさみ|カット|ハサミ|切る|文具|文房具|scissors|cut|cutting|paper|tool
🗃️	6	カードファイルボックス|カード|ファイル|ボックス|文具|文房具|箱|card file box|box|card|file
🗄️	6	ファイルキャビネット|キャビネット|ファイル|引き出し|file cabinet|cabinet|file|filing|paper
🗑️	6	ごみ箱|くずかご|ごみかご|ゴミ箱|wastebasket|can|garbage|trash|waste
🔒	6	閉じた錠|ロック|南京錠|施錠|錠|鍵|locked|closed|lock|private
🔓	6	開いた錠|ロック|南京錠|解錠|錠|鍵|unlocked|cracked|lock|open|unlock
🔏	6	閉じた錠とペン|プライバシー|ロック|南京錠|鍵|locked with pen|ink|lock|locked|nib|pen|privacy
🔐	6	閉じた錠と鍵|キー|セキュリティ|ロック|南京錠|安全|錠|鍵|locked with key|bike|closed|key|lock|locked|secure
🔑	6	鍵|キー|パスワード|開錠|key|keys|lock|major|password|unlock
🗝️	6	古い鍵|キー|パスワード|手がかり|鍵|old key|clue|key|lock|old
🔨	6	ハンマー|かなづち|とんかち|工具|hammer|home|improvement|repairs|tool
🪓	6	斧|おの|ハチェット|工具|木|薪割り|axe|ax|chop|hatchet|split|wood
⛏️	6	つるはし|工具|採掘|pick|hammer|mining|tool
⚒️	6	ハンマーとつるはし|かなづち|つるはし|とんかち|ハンマー|工具|hammer and pick|hammer|pick|tool
🛠️	6	ハンマーとレンチ|かなづち|スパナ|とんかち|レンチ|工具|hammer and wrench|hammer|spanner|tool|wrench
🗡️	6	短刀|ダガー|ナイフ|武器|短剣|dagger|knife|weapon
⚔️	6	クロスした剣|つるぎ|刀|剣|武器|crossed swords|crossed|swords|weapon
💣	6	爆弾|危険|爆発|bomb|boom|comic|dangerous|explosion|hot
🪃	6	ブーメラン|オーストラリア|武器|玩具|跳ね返り|返って来る|boomerang|rebound|repercussion|weapon
🏹	6	弓矢|射手|射手座|弓|星座|武器|矢|bow and arrow|archer|archery|arrow|bow|sagittarius|tool|weapon|zodiac
🛡️	6	盾|シールド|防具|shield|weapon
🪚	6	のこぎり|ノコ|刃|切る|大工|工具|木工|carpentry saw|carpenter|carpentry|cut|lumber|saw|tool|trim
🔧	6	レンチ|スパナ|工具|wrench|home|improvement|spanner|tool
🪛	6	ねじ回し|スクリュードライバー|ドライバー|マイナスドライバー|工具|screwdriver|flathead|handy|screw|tool
🔩	6	ボルトとナット|ナット|ボルト|工具|nut and bolt|bolt|home|improvement|nut|tool
⚙️	6	歯車|ギア|工具|gear|cog|cogwheel|tool
🗜️	6	万力|クランプ|バイス|工具|clamp|compress|tool|vice
⚖️	6	天秤|はかり|バランス|化学|天秤座|実験|星座|balance scale|balance|justice|libra|scale|scales|tool|weight|zodiac
🦯	6	白杖|アクセシビリティ|つえ|視覚|障がい|white cane|accessibility|blind|cane|probing|white
🔗	6	リンクシンボル|リンク|環|link|links
⛓️‍💥	6	壊れた鎖|くさり|チェーン|壊れた|broken chain|break|breaking|broken|chain|cuffs|freedom
⛓️	6	鎖|くさり|チェーン|chains|chain
🪝	6	フック|引っかけ|引っ掛け|掛け金|留め金|鉤|hook|catch|crook|curve|ensnare|point|selling
🧰	6	工具箱|ツールボックス|メカニック|整備士|機械工|箱|赤い箱|toolbox|box|chest|mechanic|red|tool
🧲	6	u字型磁石|マグネット|引き付ける|正負|磁石|蹄鉄|magnet|attraction|horseshoe|magnetic|negative|positive|shape|u
🪜	6	はしご|ステッパー|梯子|登る|高所作業|ladder|climb|rung|step
⚗️	6	蒸留器|化学|実験|alembic|chemistry|tool
🧪	6	試験管|ラボ|化学|化学者|実験|科学|test tube|chemist|chemistry|experiment|lab|science|test|tube
🧫	6	ペトリ皿|バクテリア|培養|実験|生物学|生物学者|petri dish|bacteria|biologist|biology|culture|dish|lab|petri
🧬	6	dna|生命|生物学|生物学者|進化|遺伝子|biologist|evolution|gene|genetics|life
🔬	6	顕微鏡|サイエンス|実験|研究|科学|microscope|experiment|lab|science|tool
🔭	6	望遠鏡|サイエンス|天体観測|科学|観測|telescope|contact|extraterrestrial|science|tool
📡	6	パラボラアンテナ|アンテナ|パラボラ|科学|衛星アンテナ|電波|satellite antenna|aliens|antenna|contact|dish|satellite|science
💉	6	注射器|予防接種|医療器具|医者|注射|病気|薬|針|syringe|doctor|flu|medicine|needle|shot|sick|tool|vaccination
🩸	6	血液|けが|出血|医療|医者|献血|生理|drop of blood|bleed|blood|donation|drop|injury|medicine|menstruation
💊	6	薬|カプセル|ビタミン剤|医者|病気|pill|doctor|drugs|medicated|medicine|pills|sick|vitamin
🩹	6	絆創膏|けが|ばんそうこう|傷|手当て|薬|adhesive bandage|adhesive|bandage
🩼	6	松葉杖|ステッキ|怪我|杖|歩行補助|障がい|crutch|aid|cane|disability|help|hurt|injured|mobility|stick
🩺	6	聴診器|医療|医者|心臓|病気|stethoscope|doctor|heart|medicine
🩻	6	x線|レントゲン|医者|骨|骨格|x-ray|bones|doctor|medical|skeleton|skull|xray
🚪	6	ドア|戸|扉|玄関|door|back|closet|front
🛗	6	エレベーター|アクセシビリティ|バリアフリー|リフト|昇降機|elevator|accessibility|hoist|lift
🪞	6	鏡|ミラー|メーク|反射|姿見|身だしなみ|mirror|makeup|reflection|reflector|speculum
🪟	6	窓|ウィンドウ|フレーム|換気|眺め|開ける|window|air|frame|fresh|opening|transparent|view
🛏️	6	ベッド|ホテル|寝る|睡眠|bed|hotel|sleep
🛋️	6	ソファとランプ|ソファ|ホテル|ランプ|長椅子|couch and lamp|couch|hotel|lamp
🪑	6	椅子|シート|チェア|家具|腰掛け|chair|seat|sit
🚽	6	便器|お手洗い|トイレ|便座|toilet|bathroom
🪠	6	ラバーカップ|すっぽん|トイレ|プランジャー|吸引カップ|通水カップ|plunger|cup|force|plumber|poop|suction|toilet
🚿	6	シャワー|風呂|shower|water
🛁	6	バスタブ|泡風呂|浴槽|風呂|bathtub|bath
🪤	6	ねずみ捕り|おびき寄せる|チーズ|ばね|マウストラップ|わな|仕掛け|mouse trap|bait|cheese|lure|mouse|mousetrap|snare|trap
🪒	6	剃刀|かみそり|ひげそり|レーザー|レザー|刃|刃物|鋭い|razor|sharp|shave
🧴	6	ローション|シャンプー|ローションボトル|化粧水|日焼け止め|lotion bottle|bottle|lotion|moisturizer|shampoo|sunscreen
🧷	6	安全ピン|止めピン|safety pin|diaper|pin|punk|rock|safety
🧹	6	ほうき|掃く|掃除|魔女|broom|cleaning|sweeping|witch
🧺	6	かご|ピクニック|収穫|洗濯|basket|farming|laundry|picnic
🧻	6	トイレットペーパー|キッチンペーパー|ペーパータオル|ロールペーパー|roll of paper|paper|roll|toilet|towels
🪣	6	バケツ|おけ|ばけつ|ポリバケツ|容器|bucket|cask|pail|vat
🧼	6	石けん|ソープ|泡|清潔|風呂|soap|bar|bathing|clean|cleaning|lather|soapdish
🫧	6	泡|あぶく|シャボン玉|バブル|水中|石けん|bubbles|bubble|burp|clean|floating|pearl|soap|underwater
🪥	6	歯ブラシ|お手入れ|ブラシ|歯磨き|洗面所|虫歯|衛生|toothbrush|bathroom|brush|clean|dental|hygiene|teeth|toiletry
🧽	6	スポンジ|吸収|掃除|海綿|sponge|absorbing|cleaning|porous|soak
🧯	6	消火器|消火|消火器具|消防|火事|fire extinguisher|extinguish|extinguisher|fire|quench
🛒	6	ショッピングカート|カート|ショッピング|スーパー|買い物|shopping cart|cart|shopping|trolley
🚬	6	煙草|たばこ|タバコ|喫煙|cigarette|smoking
⚰️	6	棺桶|吸血鬼|埋葬|棺|死|死者|coffin|dead|death|vampire
🪦	6	墓石|お墓|埋葬|墓地|墓場|石碑|追悼|headstone|cemetery|dead|grave|graveyard|memorial|rip|tomb|tombstone
⚱️	6	骨壺|埋葬|死|葬儀|遺灰|funeral urn|ashes|death|funeral|urn
🧿	6	ナザール・ボンジュウ|お守り|ナザール|ビーズ|邪視|青|魔除け|nazar amulet|amulet|bead|blue|charm|evil-eye|nazar|talisman
🪬	6	ハムサ|お守り|ファーティマ|ミリアム|手|目|護符|hamsa|amulet|fatima|fortune|guide|hand|mary|miriam|palm|protect|protection
🗿	6	モアイ|モヤイ|像|石像|顔|moai|face|moyai|statue|stoneface|travel
🪧	6	プラカード|デモ|主張|抗議|異議|看板|placard|card|demonstration|notice|picket|plaque|protest|sign
🪪	6	身分証|id|セキュリティ|免許証|身分証明書|運転免許証|identification card|card|credentials|document|identification|license|security
🏧	7	atm|現金自動預け払い機|銀行|atm sign|automated|bank|cash|money|sign|teller
🚮	7	ゴミ箱|くずかご|ゴミ捨て|ごみ箱|litter in bin sign|bin|litter|litterbin|sign
🚰	7	飲料水|水|水道|飲み水|potable water|drinking|potable|water
♿	7	車椅子|バリアフリー|バリヤフリー|wheelchair symbol|access|handicap|symbol|wheelchair
🚹	7	男子トイレ|wc|お手洗い|トイレ|化粧室|男性|men’s room|bathroom|lavatory|man|men’s|restroom|room|toilet
🚺	7	女子トイレ|wc|お手洗い|トイレ|化粧室|女性|women’s room|bathroom|lavatory|restroom|room|toilet|woman|women’s
🚻	7	トイレ|wc|お手洗い|化粧室|restroom|bathroom|lavatory|toilet
🚼	7	ベビーマーク|ベビー|ベビーシート|赤ちゃん|赤ん坊|baby symbol|baby|changing|symbol
🚾	7	wc|お手洗い|トイレ|化粧室|water closet|bathroom|closet|lavatory|restroom|toilet|water
🛂	7	出入国審査|パスポートコントロール|入出国|出入国|passport control|control|passport
🛃	7	税関|通関|customs|packing
🛄	7	手荷物受取|スーツケース|バゲッジクレーム|手荷物|預け入れ荷物|baggage claim|arrived|baggage|bags|case|checked|claim|journey|packing|plane|ready|travel|trip
🛅	7	手荷物預かり|コインロッカー|スーツケース|ロッカー|手荷物|left luggage|baggage|case|left|locker|luggage
⚠️	7	警告|標識|注意|warning|caution
🚸	7	児童横断|子供|学校、幼稚園、保育所等あり|標識|通学路|children crossing|child|children|crossing|pedestrian|traffic
⛔	7	車両進入禁止|標識|禁止|規制|進入禁止|道路|no entry|do|entry|fail|forbidden|no|not|pass|prohibited|traffic
🚫	7	車両通行止め|標識|禁止|規制|通行止め|道路|prohibited|entry|forbidden|no|not|smoke
🚳	7	自転車禁止|標識|禁止|自転車|規制|道路|no bicycles|bicycle|bicycles|bike|forbidden|no|not|prohibited
🚭	7	禁煙|タバコ|煙草|禁止|no smoking|forbidden|no|not|prohibited|smoke|smoking
🚯	7	ポイ捨て禁止|ごみ|ゴミ|ゴミ捨て禁止|禁止|no littering|forbidden|litter|littering|no|not|prohibited
🚱	7	飲用不可|禁止|飲めない水|飲用禁止|non-potable water|dry|non-drinking|non-potable|prohibited|water
🚷	7	歩行者立入禁止|標識|禁止|規制|道路|no pedestrians|forbidden|no|not|pedestrian|pedestrians|prohibited
📵	7	携帯電話禁止|スマートフォン禁止|スマホ禁止|携帯禁止|no mobile phones|cell|forbidden|mobile|no|not|phone|phones|prohibited|telephone
🔞	7	18歳未満禁止|18禁|年齢制限|未成年|禁止|no one under eighteen|18|age|eighteen|forbidden|no|not|one|prohibited|restriction|underage
☢️	7	放射能|危険|放射性|放射線|radioactive|sign
☣️	7	バイオハザード|危険|有害物質|biohazard|sign
⬆️	7	上矢印|上|北|矢印|up arrow|arrow|cardinal|direction|north|up
↗️	7	右上矢印|北東|右上|矢印|up-right arrow|arrow|direction|intercardinal|northeast|up-right
➡️	7	右矢印|右|東|矢印|right arrow|arrow|cardinal|direction|east|right
↘️	7	右下矢印|南東|右下|矢印|down-right arrow|arrow|direction|down-right|intercardinal|southeast
⬇️	7	下矢印|下|南|矢印|down arrow|arrow|cardinal|direction|down|south
↙️	7	左下矢印|南西|左下|矢印|down-left arrow|arrow|direction|down-left|intercardinal|southwest
⬅️	7	左矢印|左|矢印|西|left arrow|arrow|cardinal|direction|left|west
↖️	7	左上矢印|北西|左上|矢印|up-left arrow|arrow|direction|intercardinal|northwest|up-left
↕️	7	上下矢印|上下|矢印|up-down arrow|arrow|up-down
↔️	7	左右矢印|左右|矢印|left-right arrow|arrow|left-right
↩️	7	右カーブ矢印|カーブ|左|曲線|矢印|right arrow curving left|arrow|curving|left|right
↪️	7	左カーブ矢印|カーブ|右|曲線|矢印|left arrow curving right|arrow|curving|left|right
⤴️	7	上カーブ矢印|カーブ|上|曲線|矢印|right arrow curving up|arrow|curving|right|up
⤵️	7	下カーブ矢印|カーブ|下|曲線|矢印|right arrow curving down|arrow|curving|down|right
🔃	7	右回り縦矢印|リロード|再読み込み|右回り|時計回り|矢印|clockwise vertical arrows|arrow|arrows|clockwise|refresh|reload|vertical
🔄	7	左回り矢印|リピート|反時計回り|更新|矢印|繰り返し|逆回り|counterclockwise arrows button|again|anticlockwise|arrow|arrows|button|counterclockwise|deja|refresh|rewindershins|vu
🔙	7	back矢印|back|バック|戻る|矢印|back arrow|arrow
🔚	7	end矢印|end|エンド|矢印|終わり|end arrow|arrow
🔛	7	on矢印|on|オン|矢印|on! arrow|arrow|mark|on!
🔜	7	soon矢印|soon|すぐ|矢印|soon arrow|arrow|brb|omw
🔝	7	top矢印|top|トップ|上|矢印|top arrow|arrow|homie|up
🛐	7	礼拝所|宗教|礼拝|祈り|place of worship|place|pray|religion|worship
⚛️	7	原子のシンボル|元素|分子|原子|無神論|atom symbol|atheist|atom|symbol
🕉️	7	オーム|ヒンズー教|ヒンドゥー教|宗教|om|hindu|religion
✡️	7	ダビデの星|ユダヤ教|六芒星|宗教|星|star of david|david|jew|jewish|judaism|religion|star
☸️	7	法輪|仏教|宗教|転法輪|wheel of dharma|buddhist|dharma|religion|wheel
☯️	7	陰陽|インヤン|宗教|道教|yin yang|difficult|lives|religion|tao|taoist|total|yang|yin|yinyang
✝️	7	十字架|キリスト教|クロス|ラテン十字|宗教|latin cross|christ|christian|cross|latin|religion
☦️	7	八端十字架|キリスト教|十字架|宗教|orthodox cross|christian|cross|orthodox|religion
☪️	7	星と三日月|イスラム教|ムスリム|三日月|宗教|星|star and crescent|crescent|islam|muslim|ramadan|religion|star
☮️	7	ピースマーク|平和|平和のシンボル|peace symbol|healing|peace|peaceful|symbol
🕎	7	メノーラー|ハヌカー|メノラー|ユダヤ教|宗教|燭台|menorah|candelabrum|candlestick|hanukkah|jewish|judaism|religion
🔯	7	六芒星|ダビデ|ユダヤ教|六角星|占い|星|dotted six-pointed star|dotted|fortune|jewish|judaism|six-pointed|star
🪯	7	カンダ|カルサ|シーク教|シーク教徒|シク教|信仰|剣|宗教|khanda|deg|fateh|khalsa|religion|sikh|sikhism|tegh
♈	7	おひつじ座|星座|牡羊座|aries|horoscope|ram|zodiac
♉	7	おうし座|星座|牡牛座|taurus|bull|horoscope|ox|zodiac
♊	7	ふたご座|双子座|星座|gemini|horoscope|twins|zodiac
♋	7	かに座|星座|蟹座|cancer|crab|horoscope|zodiac
♌	7	しし座|星座|獅子座|leo|horoscope|lion|zodiac
♍	7	おとめ座|乙女座|星座|virgo|horoscope|zodiac
♎	7	てんびん座|天秤座|星座|libra|balance|horoscope|justice|scales|zodiac
♏	7	さそり座|星座|蠍座|scorpio|horoscope|scorpion|scorpius|zodiac
♐	7	いて座|射手座|星座|sagittarius|archer|horoscope|zodiac
♑	7	やぎ座|山羊座|星座|capricorn|goat|horoscope|zodiac
♒	7	みずがめ座|星座|水瓶座|aquarius|bearer|horoscope|water|zodiac
♓	7	うお座|星座|魚座|pisces|fish|horoscope|zodiac
⛎	7	へびつかい座|星座|蛇遣座|ophiuchus|bearer|serpent|snake|zodiac
🔀	7	シャッフルボタン|シャッフル|ボタン|交差|矢印|shuffle tracks button|arrow|button|crossed|shuffle|tracks
🔁	7	リピートボタン|ボタン|リピート|全曲|時計回り|矢印|繰り返し|repeat button|arrow|button|clockwise|repeat
🔂	7	1曲リピートボタン|ボタン|リピート|時計回り|矢印|繰り返し|repeat single button|arrow|button|clockwise|once|repeat|single
▶️	7	再生ボタン|プレイ|ボタン|再生|矢印|play button|arrow|button|play|right|triangle
⏩	7	早送りボタン|ボタン|倍速|早送り|矢印|fast-forward button|arrow|button|double|fast|fast-forward|forward
⏭️	7	次の曲ボタン|ボタン|次のシーン|次のトラック|矢印|next track button|arrow|button|next|scene|track|triangle
⏯️	7	再生／一時停止ボタン|ボタン|一時停止|再生|矢印|play or pause button|arrow|button|pause|play|right|triangle
◀️	7	逆再生ボタン|ボタン|巻き戻し|矢印|逆再生|reverse button|arrow|button|left|reverse|triangle
⏪	7	早戻しボタン|倍速|巻き戻し|戻る|早戻し|矢印|fast reverse button|arrow|button|double|fast|reverse|rewind
⏮️	7	前の曲ボタン|ボタン|前のシーン|前のトラック|矢印|last track button|arrow|button|last|previous|scene|track|triangle
🔼	7	上ボタン|ボタン|上|矢印|upwards button|arrow|button|red|up|upwards
⏫	7	高速上ボタン|ボタン|上|二重|矢印|fast up button|arrow|button|double|fast|up
🔽	7	下ボタン|ボタン|下|矢印|downwards button|arrow|button|down|downwards|red
⏬	7	高速下ボタン|ボタン|下|二重|矢印|fast down button|arrow|button|double|down|fast
⏸️	7	一時停止ボタン|ポーズ|ボタン|一時停止|pause button|bar|button|double|pause|vertical
⏹️	7	停止ボタン|ストップ|ボタン|停止|四角|stop button|button|square|stop
⏺️	7	録音録画ボタン|ボタン|丸|録画|録音|record button|button|circle|record
⏏️	7	取り出しボタン|イジェクト|ボタン|取り出し|eject button|button|eject
🎦	7	映画|カメラ|動画|映画館|cinema|camera|film|movie
🔅	7	低輝度|ブライトネス|明るさ|暗い|輝度|dim button|brightness|button|dim|low
🔆	7	高輝度|ブライトネス|明るい|明るさ|輝度|bright button|bright|brightness|button|light
📶	7	アンテナマーク|スマートフォン|スマホ|圏内|携帯電話|電波|antenna bars|antenna|bar|bars|cell|communication|mobile|phone|signal|telephone
🛜	7	wi-fi|インターネット|スマホ|ネットワーク|ブロードバンド|ホットスポット|接続|無線|無線lan|電波|wireless|broadband|computer|connectivity|hotspot|internet|network|router|smartphone|wifi|wlan
📳	7	マナーモード|スマートフォン|スマホ|携帯|携帯電話|vibration mode|cell|communication|mobile|mode|phone|telephone|vibration
📴	7	携帯電話電源オフ|オフ|スマートフォン|スマホ|携帯電話|電源オフ|mobile phone off|cell|mobile|off|phone|telephone
♀️	7	女性のマーク|シンボル|女|女性|female sign|female|sign|woman
♂️	7	男性のマーク|シンボル|男|男性|male sign|male|man|sign
⚧️	7	トランスジェンダーのマーク|lgbt|tg|シンボル|性別|transgender symbol|symbol|transgender
✖️	7	掛け算|キャンセル|バツ|ペケ|乗算|記号|multiply|×|cancel|multiplication|sign|x
➕	7	プラス|+|加算|記号|足し算|足す|plus
➖	7	マイナス|-|引き算|引く|減算|計算|記号|minus|−|heavy|math|sign
➗	7	割り算|/|割る|記号|除算|divide|÷|division|heavy|math|sign
🟰	7	太字の等号|イコール|数学|等しい|等号|heavy equals sign|answer|equal|equality|equals|heavy|math|sign
♾️	7	無限大|万物|永遠|無限|infinity|forever|unbounded|universal
‼️	7	二重感嘆符|!|エクスクラメーションマーク|びっくりマーク|感嘆符|約物|記号|double exclamation mark|!!|bangbang|double|exclamation|mark|punctuation
⁉️	7	感嘆符疑問符|!|!?|?|はてなマーク|びっくりマーク|記号|exclamation question mark|exclamation|interrobang|mark|punctuation|question
❓	7	赤い疑問符|?|クエスチョンマーク|はてなマーク|疑問符|約物|記号|red question mark|mark|punctuation|question|red
❔	7	白い疑問符|?|クエスチョンマーク|はてなマーク|疑問符|約物|記号|white question mark|mark|outlined|punctuation|question|white
❕	7	白い感嘆符|!|エクスクラメーションマーク|びっくりマーク|疑問符|約物|記号|white exclamation mark|bang|exclamation|mark|outlined|punctuation|white
❗	7	赤い感嘆符|!|エクスクラメーションマーク|びっくりマーク|感嘆符|約物|記号|red exclamation mark|bang|exclamation|mark|punctuation|red
〰️	7	波線|波|約物|記号|wavy dash|dash|punctuation|wavy
💱	7	外貨両替|お金|ドル|ドル円|両替|円|円ドル|通貨|currency exchange|bank|currency|exchange|money
💲	7	ドル記号|お金|ドル|通貨|heavy dollar sign|billion|cash|charge|currency|dollar|heavy|million|money|pay|sign
⚕️	7	医療のシンボル|アスクレピオスの杖|医療|医者|治療|medical symbol|aesculapius|medical|medicine|staff|symbol
♻️	7	リサイクルマーク|リサイクル|再利用|再生|recycling symbol|recycle|recycling|symbol
⚜️	7	フルール・ド・リス|エンブレム|フランス王家|ユリ|紋章|fleur-de-lis|knights
🔱	7	トライデント|エンブレム|三つ又|海|trident emblem|anchor|emblem|poseidon|ship|tool|trident
📛	7	名札|バッジ|保育園|保育所|幼稚園|name badge|badge|name
🔰	7	初心者マーク|初心者|若葉マーク|japanese symbol for beginner|beginner|chevron|green|japanese|leaf|symbol|tool|yellow
⭕	7	太い丸|円|記号|赤い丸|輪|hollow red circle|circle|heavy|hollow|large|o|red
✅	7	白抜きのチェック|チェック|チェックマーク|済|記号|check mark button|✓|button|check|checked|checkmark|complete|completed|done|fixed|mark|tick
☑️	7	チェックボックス|チェック|チェックマーク|済|記号|check box with check|✓|ballot|box|check|checked|done|off|tick
✔️	7	太字のチェック|チェック|チェックマーク|済|記号|check mark|✓|check|checked|checkmark|done|heavy|mark|tick
❌	7	バツ|キャンセル|ペケ|乗算|掛け算|記号|cross mark|×|cancel|cross|mark|multiplication|multiply|x
❎	7	白抜きのバツ|キャンセル|バツ|ペケ|乗算|掛け算|記号|cross mark button|×|button|cross|mark|multiplication|multiply|square|x
➰	7	ループ|記号|輪っか|長音|curly loop|curl|curly|loop
➿	7	ダブルループ|フリーダイヤル|ループ|記号|輪っか|double curly loop|curl|curly|double|loop
〽️	7	いおりてん|庵点|歌記号|約物|記号|part alternation mark|alternation|mark|part
✳️	7	八角アスタリスク|アスタリスク|約物|記号|eight-spoked asterisk|*|asterisk|eight-spoked
✴️	7	八角星|星|約物|記号|eight-pointed star|*|eight-pointed|star
❇️	7	スパークル|アスタリスク|キラキラ|約物|記号|sparkle|*
©️	7	著作権マーク|cマーク|コピーライト|著作権|記号|copyright|c
®️	7	登録商標マーク|rマーク|登録商標|記号|registered|r
™️	7	商標マーク|tmマーク|トレードマーク|商標|記号|trade mark|mark|tm|trade|trademark
#️⃣	7	囲み数字: #|囲み数字|keycap: #|keycap
*️⃣	7	囲み数字: *|囲み数字|keycap: *|keycap
0️⃣	7	囲み数字: 0|0|囲み数字|keycap: 0|keycap|zero
1️⃣	7	囲み数字: 1|1|一|囲み数字|keycap: 1|keycap|one
2️⃣	7	囲み数字: 2|2|二|囲み数字|keycap: 2|keycap|two
3️⃣	7	囲み数字: 3|3|三|囲み数字|keycap: 3|keycap|three
4️⃣	7	囲み数字: 4|4|四|囲み数字|keycap: 4|four|keycap
5️⃣	7	囲み数字: 5|5|五|囲み数字|keycap: 5|five|keycap
6️⃣	7	囲み数字: 6|6|六|囲み数字|keycap: 6|keycap|six
7️⃣	7	囲み数字: 7|7|七|囲み数字|keycap: 7|keycap|seven
8️⃣	7	囲み数字: 8|8|八|囲み数字|keycap: 8|eight|keycap
9️⃣	7	囲み数字: 9|9|九|囲み数字|keycap: 9|keycap|nine
🔟	7	囲み数字: 10|囲み数字|keycap: 10|keycap
🔠	7	英大文字の入力|abcd|アルファベット|ローマ字|入力|大文字|英字|input latin uppercase|input|latin|letters|uppercase
🔡	7	英小文字の入力|abcd|アルファベット|ローマ字|入力|小文字|英字|input latin lowercase|input|latin|letters|lowercase
🔢	7	数字の入力|1234|入力|数字|input numbers|input|numbers
🔣	7	記号の入力|〒♪&%|入力|記号|input symbols|&|%|♪|〒|input|symbols
🔤	7	英字の入力|abc|アルファベット|ローマ字|入力|英字|input latin letters|alphabet|input|latin|letters
🅰️	7	血液型a型|a型|血液型|a button (blood type)|blood|button|type
🆎	7	血液型ab型|ab型|血液型|ab button (blood type)|ab|blood|button|type
🅱️	7	血液型b型|b型|血液型|b button (blood type)|b|blood|button|type
🆑	7	clマーク|cl|クリア|cl button|button
🆒	7	coolマーク|cool|クール|cool button|button
🆓	7	freeマーク|タダ|フリー|無料|free button|button|free
ℹ️	7	iマーク|i|インフォメーション|情報|案内所|information
🆔	7	idマーク|id|アイディー|id button|button|identity
Ⓜ️	7	丸いmマーク|m|丸|circled m|circle|circled
🆕	7	newマーク|new|新|新しい|new button|button
🆖	7	ngマーク|ng|エヌジー|ボツ|ng button|button
🅾️	7	血液型o型|o型|血液型|o button (blood type)|blood|button|o|type
🆗	7	okマーク|ok|オーケー|オッケー|ok button|button|okay
🅿️	7	pマーク|p|パーキング|駐車場|p button|button|parking
🆘	7	sosマーク|sos|エスオーエス|助けて|sos button|button|help
🆙	7	upマーク|up|アップ|更新|up! button|button|mark|up!
🆚	7	vsマーク|ヴァーサス|ヴィエス|バーサス|ブイエス|対|vs button|button|versus|vs
🈁	7	ココのマーク|ここ|ココ|japanese “here” button|button|here|japanese|katakana
🈂️	7	サのマーク|サ|サービス料|japanese “service charge” button|button|charge|japanese|katakana|service
🈷️	7	月マーク|月|月極|月額|japanese “monthly amount” button|amount|button|ideograph|japanese|monthly
🈶	7	有マーク|有|有料|有限|japanese “not free of charge” button|button|charge|free|ideograph|japanese|not
🈯	7	指マーク|指|指定席|japanese “reserved” button|button|ideograph|japanese|reserved
🉐	7	マル得|お得|バーゲン|得|japanese “bargain” button|bargain|button|ideograph|japanese
🈹	7	割マーク|ディスカウント|割|割引|japanese “discount” button|button|discount|ideograph|japanese
🈚	7	無マーク|タダ|無|無料|japanese “free of charge” button|button|charge|free|ideograph|japanese
🈲	7	禁マーク|厳禁|禁|禁止|japanese “prohibited” button|button|ideograph|japanese|prohibited
🉑	7	可マーク|可|可能|許可|japanese “acceptable” button|acceptable|button|ideograph|japanese
🈸	7	申マーク|申|申し込み|申請|japanese “application” button|application|button|ideograph|japanese
🈴	7	合マーク|合|合格|japanese “passing grade” button|button|grade|ideograph|japanese|passing
🈳	7	空マーク|空|空き|空室|空席|japanese “vacancy” button|button|ideograph|japanese|vacancy
㊗️	7	祝マーク|おめでとう|お祝い|祝|japanese “congratulations” button|button|congratulations|ideograph|japanese
㊙️	7	マル秘|極秘|社外秘|秘|秘密|japanese “secret” button|button|ideograph|japanese|secret
🈺	7	営マーク|営|営業中|営業日|japanese “open for business” button|business|button|ideograph|japanese|open
🈵	7	満マーク|満員|満室|満席|満車|japanese “no vacancy” button|button|ideograph|japanese|no|vacancy
🔴	7	赤い丸|丸|丸いボタン|赤|red circle|circle|geometric|red
🟠	7	オレンジの丸|オレンジ|丸|丸いボタン|orange circle|circle|orange
🟡	7	黄色い丸|丸|丸いボタン|黄色|yellow circle|circle|yellow
🟢	7	緑の丸|丸|丸いボタン|緑|green circle|circle|green
🔵	7	青い丸|丸|丸いボタン|青|blue circle|blue|circle|geometric
🟣	7	紫の丸|丸|丸いボタン|紫|purple circle|circle|purple
🟤	7	茶色の丸|丸|丸いボタン|茶色|brown circle|brown|circle
⚫	7	黒い丸|丸|丸いボタン|黒|黒丸|black circle|black|circle|geometric
⚪	7	白い丸|丸|丸いボタン|白|white circle|circle|geometric|white
🟥	7	赤い四角|四角|正方形|赤|red square|card|penalty|red|square
🟧	7	オレンジの四角|オレンジ|四角|正方形|orange square|orange|square
🟨	7	黄色い四角|四角|正方形|黄色|yellow square|card|penalty|square|yellow
🟩	7	緑の四角|四角|正方形|緑|green square|green|square
🟦	7	青い四角|四角|正方形|青|blue square|blue|square
🟪	7	紫の四角|四角|正方形|紫|purple square|purple|square
🟫	7	茶色い四角|四角|正方形|茶色|brown square|brown|square
⬛	7	黒四角特大|四角|正方形|黒|black large square|black|geometric|large|square
⬜	7	白四角特大|四角|正方形|白|white large square|geometric|large|square|white
◼️	7	黒四角大|四角|正方形|黒|black medium square|black|geometric|medium|square
◻️	7	白四角大|四角|正方形|白|white medium square|geometric|medium|square|white
◾	7	黒四角中|四角|正方形|黒|black medium-small square|black|geometric|medium-small|square
◽	7	白四角中|四角|正方形|白|white medium-small square|geometric|medium-small|square|white
▪️	7	黒四角小|四角|正方形|黒|black small square|black|geometric|small|square
▫️	7	白四角小|四角|正方形|白|white small square|geometric|small|square|white
🔶	7	大きいオレンジのひし形|オレンジ|ダイア|ダイヤ|ひし形|四角|large orange diamond|diamond|geometric|large|orange
🔷	7	大きい青のひし形|ダイア|ダイヤ|ひし形|四角|青|large blue diamond|blue|diamond|geometric|large
🔸	7	小さいオレンジのひし形|オレンジ|ダイア|ダイヤ|ひし形|四角|small orange diamond|diamond|geometric|orange|small
🔹	7	小さい青のひし形|ダイア|ダイヤ|ひし形|四角|青|small blue diamond|blue|diamond|geometric|small
🔺	7	赤い上三角|三角|上|赤|red triangle pointed up|geometric|pointed|red|triangle|up
🔻	7	赤い下三角|三角|下|赤|red triangle pointed down|down|geometric|pointed|red|triangle
💠	7	ドット付きひし形|ダイア|ダイヤ|ひし形|diamond with a dot|comic|diamond|dot|geometric
🔘	7	ラジオボタン|ボタン|二重丸|radio button|button|geometric|radio
🔳	7	白枠四角|ボタン|四角ボタン|正方形|white square button|button|geometric|outlined|square|white
🔲	7	黒枠四角|ボタン|四角ボタン|正方形|black square button|black|button|geometric|square
🏁	8	チェッカーフラッグ|スポーツ|フラグ|フラッグ|レーシング|レース|勝利|旗|chequered flag|checkered|chequered|finish|flag|flags|game|race|racing|sport|win
🚩	8	三角の旗|ゴルフ|スポーツ|フラグ|フラッグ|建築|旗|triangular flag|construction|flag|golf|post|triangular
🎌	8	祝日の旗|フラグ|フラッグ|交差した旗|旗|日の丸|日本|祝日|crossed flags|celebration|cross|crossed|flags|japanese
🏴	8	黒旗|フラグ|フラッグ|旗|黒|black flag|black|flag|waving
🏳️	8	白旗|フラグ|フラッグ|旗|白|white flag|flag|waving|white
🏳️‍🌈	8	レインボーフラッグ|glbt|lgbt|lgbtq|lgbtqia|ゲイ|トランスジェンダー|バイセクシュアル|プライド|フラグ|レズビアン|虹色の旗|rainbow flag|bisexual|flag|gay|genderqueer|glbtq|lesbian|pride|queer|rainbow|trans|transgender
🏳️‍⚧️	8	トランスジェンダーフラッグ|lgbt|tg|トランスジェンダー|フラグ|フラッグ|旗|transgender flag|blue|flag|light|pink|transgender|white
🏴‍☠️	8	海賊旗|ジョリー・ロジャー|宝物|海賊|略奪|pirate flag|flag|jolly|pirate|plunder|roger|treasure
🇦🇨	8	旗: アセンション島|旗|flag: ascension island|flag
🇦🇩	8	旗: アンドラ|旗|flag: andorra|flag
🇦🇪	8	旗: アラブ首長国連邦|旗|flag: united arab emirates|flag
🇦🇫	8	旗: アフガニスタン|旗|flag: afghanistan|flag
🇦🇬	8	旗: アンティグア・バーブーダ|旗|flag: antigua & barbuda|flag
🇦🇮	8	旗: アンギラ|旗|flag: anguilla|flag
🇦🇱	8	旗: アルバニア|旗|flag: albania|flag
🇦🇲	8	旗: アルメニア|旗|flag: armenia|flag
🇦🇴	8	旗: アンゴラ|旗|flag: angola|flag
🇦🇶	8	旗: 南極|旗|flag: antarctica|flag
🇦🇷	8	旗: アルゼンチン|旗|flag: argentina|flag
🇦🇸	8	旗: 米領サモア|旗|flag: american samoa|flag
🇦🇹	8	旗: オーストリア|旗|flag: austria|flag
🇦🇺	8	旗: オーストラリア|旗|flag: australia|flag
🇦🇼	8	旗: アルバ|旗|flag: aruba|flag
🇦🇽	8	旗: オーランド諸島|旗|flag: åland islands|flag
🇦🇿	8	旗: アゼルバイジャン|旗|flag: azerbaijan|flag
🇧🇦	8	旗: ボスニア・ヘルツェゴビナ|旗|flag: bosnia & herzegovina|flag
🇧🇧	8	旗: バルバドス|旗|flag: barbados|flag
🇧🇩	8	旗: バングラデシュ|旗|flag: bangladesh|flag
🇧🇪	8	旗: ベルギー|旗|flag: belgium|flag
🇧🇫	8	旗: ブルキナファソ|旗|flag: burkina faso|flag
🇧🇬	8	旗: ブルガリア|旗|flag: bulgaria|flag
🇧🇭	8	旗: バーレーン|旗|flag: bahrain|flag
🇧🇮	8	旗: ブルンジ|旗|flag: burundi|flag
🇧🇯	8	旗: ベナン|旗|flag: benin|flag
🇧🇱	8	旗: サン・バルテルミー|旗|flag: st. barthélemy|flag
🇧🇲	8	旗: バミューダ|旗|flag: bermuda|flag
🇧🇳	8	旗: ブルネイ|旗|flag: brunei|flag
🇧🇴	8	旗: ボリビア|旗|flag: bolivia|flag
🇧🇶	8	旗: オランダ領カリブ|旗|flag: caribbean netherlands|flag
🇧🇷	8	旗: ブラジル|旗|flag: brazil|flag
🇧🇸	8	旗: バハマ|旗|flag: bahamas|flag
🇧🇹	8	旗: ブータン|旗|flag: bhutan|flag
🇧🇻	8	旗: ブーベ島|旗|flag: bouvet island|flag
🇧🇼	8	旗: ボツワナ|旗|flag: botswana|flag
🇧🇾	8	旗: ベラルーシ|旗|flag: belarus|flag
🇧🇿	8	旗: ベリーズ|旗|flag: belize|flag
🇨🇦	8	旗: カナダ|旗|flag: canada|flag
🇨🇨	8	旗: ココス(キーリング)諸島|旗|flag: cocos (keeling) islands|flag
🇨🇩	8	旗: コンゴ民主共和国(キンシャサ)|旗|flag: congo - kinshasa|flag
🇨🇫	8	旗: 中央アフリカ共和国|旗|flag: central african republic|flag
🇨🇬	8	旗: コンゴ共和国(ブラザビル)|旗|flag: congo - brazzaville|flag
🇨🇭	8	旗: スイス|旗|flag: switzerland|flag
🇨🇮	8	旗: コートジボワール|旗|flag: côte d’ivoire|flag
🇨🇰	8	旗: クック諸島|旗|flag: cook islands|flag
🇨🇱	8	旗: チリ|旗|flag: chile|flag
🇨🇲	8	旗: カメルーン|旗|flag: cameroon|flag
🇨🇳	8	旗: 中国|旗|flag: china|flag
🇨🇴	8	旗: コロンビア|旗|flag: colombia|flag
🇨🇵	8	旗: クリッパートン島|旗|flag: clipperton island|flag
🇨🇷	8	旗: コスタリカ|旗|flag: costa rica|flag
🇨🇺	8	旗: キューバ|旗|flag: cuba|flag
🇨🇻	8	旗: カーボベルデ|旗|flag: cape verde|flag
🇨🇼	8	旗: キュラソー|旗|flag: curaçao|flag
🇨🇽	8	旗: クリスマス島|旗|flag: christmas island|flag
🇨🇾	8	旗: キプロス|旗|flag: cyprus|flag
🇨🇿	8	旗: チェコ|旗|flag: czechia|flag
🇩🇪	8	旗: ドイツ|旗|flag: germany|flag
🇩🇬	8	旗: ディエゴガルシア島|旗|flag: diego garcia|flag
🇩🇯	8	旗: ジブチ|旗|flag: djibouti|flag
🇩🇰	8	旗: デンマーク|旗|flag: denmark|flag
🇩🇲	8	旗: ドミニカ国|旗|flag: dominica|flag
🇩🇴	8	旗: ドミニカ共和国|旗|flag: dominican republic|flag
🇩🇿	8	旗: アルジェリア|旗|flag: algeria|flag
🇪🇦	8	旗: セウタ・メリリャ|旗|flag: ceuta & melilla|flag
🇪🇨	8	旗: エクアドル|旗|flag: ecuador|flag
🇪🇪	8	旗: エストニア|旗|flag: estonia|flag
🇪🇬	8	旗: エジプト|旗|flag: egypt|flag
🇪🇭	8	旗: 西サハラ|旗|flag: western sahara|flag
🇪🇷	8	旗: エリトリア|旗|flag: eritrea|flag
🇪🇸	8	旗: スペイン|旗|flag: spain|flag
🇪🇹	8	旗: エチオピア|旗|flag: ethiopia|flag
🇪🇺	8	旗: 欧州連合|旗|flag: european union|flag
🇫🇮	8	旗: フィンランド|旗|flag: finland|flag
🇫🇯	8	旗: フィジー|旗|flag: fiji|flag
🇫🇰	8	旗: フォークランド諸島|旗|flag: falkland islands|flag
🇫🇲	8	旗: ミクロネシア連邦|旗|flag: micronesia|flag
🇫🇴	8	旗: フェロー諸島|旗|flag: faroe islands|flag
🇫🇷	8	旗: フランス|旗|flag: france|flag
🇬🇦	8	旗: ガボン|旗|flag: gabon|flag
🇬🇧	8	旗: イギリス|旗|flag: united kingdom|flag
🇬🇩	8	旗: グレナダ|旗|flag: grenada|flag
🇬🇪	8	旗: ジョージア|旗|flag: georgia|flag
🇬🇫	8	旗: 仏領ギアナ|旗|flag: french guiana|flag
🇬🇬	8	旗: ガーンジー|旗|flag: guernsey|flag
🇬🇭	8	旗: ガーナ|旗|flag: ghana|flag
🇬🇮	8	旗: ジブラルタル|旗|flag: gibraltar|flag
🇬🇱	8	旗: グリーンランド|旗|flag: greenland|flag
🇬🇲	8	旗: ガンビア|旗|flag: gambia|flag
🇬🇳	8	旗: ギニア|旗|flag: guinea|flag
🇬🇵	8	旗: グアドループ|旗|flag: guadeloupe|flag
🇬🇶	8	旗: 赤道ギニア|旗|flag: equatorial guinea|flag
🇬🇷	8	旗: ギリシャ|旗|flag: greece|flag
🇬🇸	8	旗: サウスジョージア・サウスサンドウィッチ諸島|旗|flag: south georgia & south sandwich islands|flag
🇬🇹	8	旗: グアテマラ|旗|flag: guatemala|flag
🇬🇺	8	旗: グアム|旗|flag: guam|flag
🇬🇼	8	旗: ギニアビサウ|旗|flag: guinea-bissau|flag
🇬🇾	8	旗: ガイアナ|旗|flag: guyana|flag
🇭🇰	8	旗: 中華人民共和国香港特別行政区|旗|flag: hong kong sar china|flag
🇭🇲	8	旗: ハード島・マクドナルド諸島|旗|flag: heard island & mcdonald islands|flag
🇭🇳	8	旗: ホンジュラス|旗|flag: honduras|flag
🇭🇷	8	旗: クロアチア|旗|flag: croatia|flag
🇭🇹	8	旗: ハイチ|旗|flag: haiti|flag
🇭🇺	8	旗: ハンガリー|旗|flag: hungary|flag
🇮🇨	8	旗: カナリア諸島|旗|flag: canary islands|flag
🇮🇩	8	旗: インドネシア|旗|flag: indonesia|flag
🇮🇪	8	旗: アイルランド|旗|flag: ireland|flag
🇮🇱	8	旗: イスラエル|旗|flag: israel|flag
🇮🇲	8	旗: マン島|旗|flag: isle of man|flag
🇮🇳	8	旗: インド|旗|flag: india|flag
🇮🇴	8	旗: 英領インド洋地域|旗|flag: british indian ocean territory|flag
🇮🇶	8	旗: イラク|旗|flag: iraq|flag
🇮🇷	8	旗: イラン|旗|flag: iran|flag
🇮🇸	8	旗: アイスランド|旗|flag: iceland|flag
🇮🇹	8	旗: イタリア|旗|flag: italy|flag
🇯🇪	8	旗: ジャージー|旗|flag: jersey|flag
🇯🇲	8	旗: ジャマイカ|旗|flag: jamaica|flag
🇯🇴	8	旗: ヨルダン|旗|flag: jordan|flag
"""

private const val UNICODE_EMOJI_DATA_4: String = """
🇯🇵	8	旗: 日本|旗|flag: japan|flag
🇰🇪	8	旗: ケニア|旗|flag: kenya|flag
🇰🇬	8	旗: キルギス|旗|flag: kyrgyzstan|flag
🇰🇭	8	旗: カンボジア|旗|flag: cambodia|flag
🇰🇮	8	旗: キリバス|旗|flag: kiribati|flag
🇰🇲	8	旗: コモロ|旗|flag: comoros|flag
🇰🇳	8	旗: セントクリストファー・ネーヴィス|旗|flag: st. kitts & nevis|flag
🇰🇵	8	旗: 北朝鮮|旗|flag: north korea|flag
🇰🇷	8	旗: 韓国|旗|flag: south korea|flag
🇰🇼	8	旗: クウェート|旗|flag: kuwait|flag
🇰🇾	8	旗: ケイマン諸島|旗|flag: cayman islands|flag
🇰🇿	8	旗: カザフスタン|旗|flag: kazakhstan|flag
🇱🇦	8	旗: ラオス|旗|flag: laos|flag
🇱🇧	8	旗: レバノン|旗|flag: lebanon|flag
🇱🇨	8	旗: セントルシア|旗|flag: st. lucia|flag
🇱🇮	8	旗: リヒテンシュタイン|旗|flag: liechtenstein|flag
🇱🇰	8	旗: スリランカ|旗|flag: sri lanka|flag
🇱🇷	8	旗: リベリア|旗|flag: liberia|flag
🇱🇸	8	旗: レソト|旗|flag: lesotho|flag
🇱🇹	8	旗: リトアニア|旗|flag: lithuania|flag
🇱🇺	8	旗: ルクセンブルク|旗|flag: luxembourg|flag
🇱🇻	8	旗: ラトビア|旗|flag: latvia|flag
🇱🇾	8	旗: リビア|旗|flag: libya|flag
🇲🇦	8	旗: モロッコ|旗|flag: morocco|flag
🇲🇨	8	旗: モナコ|旗|flag: monaco|flag
🇲🇩	8	旗: モルドバ|旗|flag: moldova|flag
🇲🇪	8	旗: モンテネグロ|旗|flag: montenegro|flag
🇲🇫	8	旗: サン・マルタン|旗|flag: st. martin|flag
🇲🇬	8	旗: マダガスカル|旗|flag: madagascar|flag
🇲🇭	8	旗: マーシャル諸島|旗|flag: marshall islands|flag
🇲🇰	8	旗: 北マケドニア|旗|flag: north macedonia|flag
🇲🇱	8	旗: マリ|旗|flag: mali|flag
🇲🇲	8	旗: ミャンマー (ビルマ)|旗|flag: myanmar (burma)|flag
🇲🇳	8	旗: モンゴル|旗|flag: mongolia|flag
🇲🇴	8	旗: 中華人民共和国マカオ特別行政区|旗|flag: macao sar china|flag
🇲🇵	8	旗: 北マリアナ諸島|旗|flag: northern mariana islands|flag
🇲🇶	8	旗: マルティニーク|旗|flag: martinique|flag
🇲🇷	8	旗: モーリタニア|旗|flag: mauritania|flag
🇲🇸	8	旗: モントセラト|旗|flag: montserrat|flag
🇲🇹	8	旗: マルタ|旗|flag: malta|flag
🇲🇺	8	旗: モーリシャス|旗|flag: mauritius|flag
🇲🇻	8	旗: モルディブ|旗|flag: maldives|flag
🇲🇼	8	旗: マラウイ|旗|flag: malawi|flag
🇲🇽	8	旗: メキシコ|旗|flag: mexico|flag
🇲🇾	8	旗: マレーシア|旗|flag: malaysia|flag
🇲🇿	8	旗: モザンビーク|旗|flag: mozambique|flag
🇳🇦	8	旗: ナミビア|旗|flag: namibia|flag
🇳🇨	8	旗: ニューカレドニア|旗|flag: new caledonia|flag
🇳🇪	8	旗: ニジェール|旗|flag: niger|flag
🇳🇫	8	旗: ノーフォーク島|旗|flag: norfolk island|flag
🇳🇬	8	旗: ナイジェリア|旗|flag: nigeria|flag
🇳🇮	8	旗: ニカラグア|旗|flag: nicaragua|flag
🇳🇱	8	旗: オランダ|旗|flag: netherlands|flag
🇳🇴	8	旗: ノルウェー|旗|flag: norway|flag
🇳🇵	8	旗: ネパール|旗|flag: nepal|flag
🇳🇷	8	旗: ナウル|旗|flag: nauru|flag
🇳🇺	8	旗: ニウエ|旗|flag: niue|flag
🇳🇿	8	旗: ニュージーランド|旗|flag: new zealand|flag
🇴🇲	8	旗: オマーン|旗|flag: oman|flag
🇵🇦	8	旗: パナマ|旗|flag: panama|flag
🇵🇪	8	旗: ペルー|旗|flag: peru|flag
🇵🇫	8	旗: 仏領ポリネシア|旗|flag: french polynesia|flag
🇵🇬	8	旗: パプアニューギニア|旗|flag: papua new guinea|flag
🇵🇭	8	旗: フィリピン|旗|flag: philippines|flag
🇵🇰	8	旗: パキスタン|旗|flag: pakistan|flag
🇵🇱	8	旗: ポーランド|旗|flag: poland|flag
🇵🇲	8	旗: サンピエール島・ミクロン島|旗|flag: st. pierre & miquelon|flag
🇵🇳	8	旗: ピトケアン諸島|旗|flag: pitcairn islands|flag
🇵🇷	8	旗: プエルトリコ|旗|flag: puerto rico|flag
🇵🇸	8	旗: パレスチナ自治区|旗|flag: palestinian territories|flag
🇵🇹	8	旗: ポルトガル|旗|flag: portugal|flag
🇵🇼	8	旗: パラオ|旗|flag: palau|flag
🇵🇾	8	旗: パラグアイ|旗|flag: paraguay|flag
🇶🇦	8	旗: カタール|旗|flag: qatar|flag
🇷🇪	8	旗: レユニオン|旗|flag: réunion|flag
🇷🇴	8	旗: ルーマニア|旗|flag: romania|flag
🇷🇸	8	旗: セルビア|旗|flag: serbia|flag
🇷🇺	8	旗: ロシア|旗|flag: russia|flag
🇷🇼	8	旗: ルワンダ|旗|flag: rwanda|flag
🇸🇦	8	旗: サウジアラビア|旗|flag: saudi arabia|flag
🇸🇧	8	旗: ソロモン諸島|旗|flag: solomon islands|flag
🇸🇨	8	旗: セーシェル|旗|flag: seychelles|flag
🇸🇩	8	旗: スーダン|旗|flag: sudan|flag
🇸🇪	8	旗: スウェーデン|旗|flag: sweden|flag
🇸🇬	8	旗: シンガポール|旗|flag: singapore|flag
🇸🇭	8	旗: セントヘレナ|旗|flag: st. helena, ascension & tristan da cunha|flag
🇸🇮	8	旗: スロベニア|旗|flag: slovenia|flag
🇸🇯	8	旗: スバールバル諸島・ヤンマイエン島|旗|flag: svalbard & jan mayen|flag
🇸🇰	8	旗: スロバキア|旗|flag: slovakia|flag
🇸🇱	8	旗: シエラレオネ|旗|flag: sierra leone|flag
🇸🇲	8	旗: サンマリノ|旗|flag: san marino|flag
🇸🇳	8	旗: セネガル|旗|flag: senegal|flag
🇸🇴	8	旗: ソマリア|旗|flag: somalia|flag
🇸🇷	8	旗: スリナム|旗|flag: suriname|flag
🇸🇸	8	旗: 南スーダン|旗|flag: south sudan|flag
🇸🇹	8	旗: サントメ・プリンシペ|旗|flag: são tomé & príncipe|flag
🇸🇻	8	旗: エルサルバドル|旗|flag: el salvador|flag
🇸🇽	8	旗: シント・マールテン|旗|flag: sint maarten|flag
🇸🇾	8	旗: シリア|旗|flag: syria|flag
🇸🇿	8	旗: エスワティニ|旗|flag: eswatini|flag
🇹🇦	8	旗: トリスタン・ダ・クーニャ|旗|flag: tristan da cunha|flag
🇹🇨	8	旗: タークス・カイコス諸島|旗|flag: turks & caicos islands|flag
🇹🇩	8	旗: チャド|旗|flag: chad|flag
🇹🇫	8	旗: 仏領極南諸島|旗|flag: french southern and antarctic lands|flag
🇹🇬	8	旗: トーゴ|旗|flag: togo|flag
🇹🇭	8	旗: タイ|旗|flag: thailand|flag
🇹🇯	8	旗: タジキスタン|旗|flag: tajikistan|flag
🇹🇰	8	旗: トケラウ|旗|flag: tokelau|flag
🇹🇱	8	旗: 東ティモール|旗|flag: timor-leste|flag
🇹🇲	8	旗: トルクメニスタン|旗|flag: turkmenistan|flag
🇹🇳	8	旗: チュニジア|旗|flag: tunisia|flag
🇹🇴	8	旗: トンガ|旗|flag: tonga|flag
🇹🇷	8	旗: トルコ|旗|flag: türkiye|flag
🇹🇹	8	旗: トリニダード・トバゴ|旗|flag: trinidad & tobago|flag
🇹🇻	8	旗: ツバル|旗|flag: tuvalu|flag
🇹🇼	8	旗: 台湾|旗|flag: taiwan|flag
🇹🇿	8	旗: タンザニア|旗|flag: tanzania|flag
🇺🇦	8	旗: ウクライナ|旗|flag: ukraine|flag
🇺🇬	8	旗: ウガンダ|旗|flag: uganda|flag
🇺🇲	8	旗: 合衆国領有小離島|旗|flag: u.s. outlying islands|flag
🇺🇳	8	旗: 国際連合|旗|flag: united nations|flag
🇺🇸	8	旗: アメリカ合衆国|旗|flag: united states|flag
🇺🇾	8	旗: ウルグアイ|旗|flag: uruguay|flag
🇺🇿	8	旗: ウズベキスタン|旗|flag: uzbekistan|flag
🇻🇦	8	旗: バチカン市国|旗|flag: vatican city|flag
🇻🇨	8	旗: セントビンセント及びグレナディーン諸島|旗|flag: st. vincent & grenadines|flag
🇻🇪	8	旗: ベネズエラ|旗|flag: venezuela|flag
🇻🇬	8	旗: 英領ヴァージン諸島|旗|flag: british virgin islands|flag
🇻🇮	8	旗: 米領ヴァージン諸島|旗|flag: u.s. virgin islands|flag
🇻🇳	8	旗: ベトナム|旗|flag: vietnam|flag
🇻🇺	8	旗: バヌアツ|旗|flag: vanuatu|flag
🇼🇫	8	旗: ウォリス・フツナ|旗|flag: wallis & futuna|flag
🇼🇸	8	旗: サモア|旗|flag: samoa|flag
🇽🇰	8	旗: コソボ|旗|flag: kosovo|flag
🇾🇪	8	旗: イエメン|旗|flag: yemen|flag
🇾🇹	8	旗: マヨット|旗|flag: mayotte|flag
🇿🇦	8	旗: 南アフリカ|旗|flag: south africa|flag
🇿🇲	8	旗: ザンビア|旗|flag: zambia|flag
🇿🇼	8	旗: ジンバブエ|旗|flag: zimbabwe|flag
🏴󠁧󠁢󠁥󠁮󠁧󠁿	8	旗: イングランド|旗|flag: england|flag
🏴󠁧󠁢󠁳󠁣󠁴󠁿	8	旗: スコットランド|旗|flag: scotland|flag
🏴󠁧󠁢󠁷󠁬󠁳󠁿	8	旗: ウェールズ|旗|flag: wales|flag
"""
