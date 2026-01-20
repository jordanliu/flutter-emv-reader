package com.paytrek.stater

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import com.github.devnied.emvnfccard.parser.EmvTemplate
import com.github.devnied.emvnfccard.model.enums.CardStateEnum
import java.io.IOException
import java.text.SimpleDateFormat

class NfcScanner(private val plugin: EmvCardReaderPlugin) : NfcAdapter.ReaderCallback {
    private val handler = Handler(Looper.getMainLooper())
    private val isProcessing = AtomicBoolean(false)

    override fun onTagDiscovered(tag: Tag) {
        val sink = plugin.sink ?: return
        if (isProcessing.getAndSet(true)) {
            return
        }

        var success = false
        var attempts = 0

        try {
            while (attempts < 3) {
                attempts++
                var id: IsoDep? = null
                try {
                    id = IsoDep.get(tag)
                    if (id == null) {
                         // If we can't get IsoDep, it's likely fatal or not an IsoDep tag
                         handler.post{ sink.success(null) }
                         return
                    }
                    
                    // Attempt to connect
                    if (!id.isConnected) {
                        id.connect()
                    }
                    
                    // Increase timeout to 2 seconds to handle slow cards/complex transactions
                    id.timeout = 2000

                    val provider = IsoDepProvider(id)
                    val config = EmvTemplate.Config()
                    val parser = EmvTemplate.Builder().setProvider(provider).setConfig(config).build()
                    val card = parser.readEmvCard()

                    var number: String? = null
                    var expire: String? = null
                    var holder: String? = null
                    var type: String? = null
                    var status: String? = null

                    val fmt = SimpleDateFormat("MM/YY")

                    if (card.track1 != null) {
                        number = card.track1.cardNumber
                        expire = fmt.format(card.track1.expireDate)
                    } else if (card.track2 != null) {
                        number = card.track2.cardNumber
                        expire = fmt.format(card.track2.expireDate)
                    }

                    if (card.holderFirstname != null && card.holderLastname != null) {
                        holder = card.holderFirstname + " " + card.holderLastname
                    }

                    if (card.type != null) {
                        type = card.type.name
                    }

                    if (card.state == CardStateEnum.UNKNOWN) {
                        status = "unknown"
                    } else if (card.state == CardStateEnum.LOCKED) {
                        status = "locked"
                    } else if (card.state == CardStateEnum.ACTIVE) {
                        status = "active"
                    }

                    val res = HashMap<String, String?>()
                    res.put("type", type)
                    res.put("number", number)
                    res.put("expire", expire)
                    res.put("holder", holder)
                    res.put("status", status)

                    if (number == null) {
                        // unexpected empty read, retry
                        try { id.close() } catch (ignored: Exception) {}
                         try { Thread.sleep(100) } catch (ignored: Exception) {}
                        continue
                    }

                    handler.post{ sink.success(res) }
                    success = true
                    try { id.close() } catch (ignored: Exception) {}
                    break

                } catch (e: Exception) {
                    // Treat SecurityException "Tag ... is out of date" like a lost tag
                    if (e is SecurityException) {
                        break
                    }
                    // If tag is lost, we must break the loop to allow the system to rediscover the tag (or a new one).
                    // Looping on a lost tag will block the NFC service forever.
                    if (e is android.nfc.TagLostException || e.cause is android.nfc.TagLostException) {
                        break
                    }
                    // If another tech is still connected, close and retry once
                    if (e is IOException && e.message?.contains("Only one TagTechnology") == true) {
                        try { id?.close() } catch (ignored: Exception) {}
                        break
                    }

                    // Slight delay before retry for other errors (e.g. transmission noise)
                    try { Thread.sleep(100) } catch (ignored: Exception) {}
                    try { id?.close() } catch (ignored: Exception) {}
                } finally {
                    if (success) {
                         try { id?.close() } catch (ignored: Exception) {}
                    }
                }
            }
        } finally {
            isProcessing.set(false)
        }
    }
}
