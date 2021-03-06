[//]: # (ПРИМЕЧАНИЕ: пожалуйста, помещайте каждое предложение в свою собственную строку, Transifex помещает каждую строку в свою область перевода!)

## Подтверждение ключей
Без подтверждения вы не можете быть уверены, что ключ принадлежит определённому человеку.
Простейший способ подтвердить ключ — отсканировать QR-код или получить его через NFC.
Для подтверждения ключей более чем двух человек, мы рекомендуем использовать один из доступных методов обмена ключами.

## Статус ключей

<img src="status_signature_verified_cutout_24dp"/>  
Подтверждён: Вы уже подтвердили этот ключ, напр. отсканировав QR код.  
<img src="status_signature_unverified_cutout_24dp"/>  
Не подтверждён: Этот ключ ещё не прошел проверку. Вы не можете быть уверены, что ключ принадлежит определенному человеку.  
<img src="status_signature_expired_cutout_24dp"/>  
Просрочен: Срок годности ключа истёк. Только его владелец может продлить срок годности.  
<img src="status_signature_revoked_cutout_24dp"/>  
Отозван: Этот ключ больше не действителен. Владелец ключа отозвал его.

## Подробная информация
"Подтверждение ключей" в OpenKeychain реализовано методом сертификации, согласно стандарту OpenPGP.
Эта сертификация представляет собой ["generic certification (0x10)"](http://tools.ietf.org/html/rfc4880#section-5.2.1) , описанной в стандарте:
"Издатель такой подписи (поручитель) никак не оговаривает, что провёл какую-то проверку ключа и его связь с лицом, чьё имя указано в сертификате."

Традиционно, сертификации (также с более высоким уровнем сертификации, такие как "позитивные сертификации" (0x13)) организованы в OpenPGP's Web of Trust.
Наша модель подтверждения ключа является гораздо более простой концепцией, чтобы избежать наиболее распространённых проблем использования, связанных с Web of Trust.
Мы предполагаем, что ключи проверяются только до определённой степени, приемлемой для выполнения "на ходу".
Мы также не осуществляем (потенциально транзитивные) доверенные подписи или базы данных доверенных владельцев как в GnuPG.
Кроме того, ключи, которые содержат по меньшей мере один идентификатор пользователя, сертифицированный доверенным ключом, будут отмечаться как "подтверждённые" в списке ключей.