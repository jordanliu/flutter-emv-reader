package com.paytrek.stater

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import com.github.devnied.emvnfccard.parser.EmvTemplate
import com.github.devnied.emvnfccard.model.enums.CardStateEnum
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.IOException
import java.text.SimpleDateFormat

sealed class AbstractNfcHandler(val result: MethodChannel.Result, val call: MethodCall) : NfcAdapter.ReaderCallback {
    protected fun unregister() = EmvCardReaderPlugin.listeners.remove(this)
}

class NfcReader(result: MethodChannel.Result, call: MethodCall) : AbstractNfcHandler(result, call) {
    private val handler = Handler(Looper.getMainLooper())

    override fun onTagDiscovered(tag: Tag) {
        var success = false

        while (true) {
            var id: IsoDep? = null
            try {
                id = IsoDep.get(tag)
                
                if (id == null) {
                     // If we fail to get IsoDep, break and return null as it's likely a fatal type mismatch
                     handler.post{ result.success(null) }
                     unregister()
                     return
                }

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
                    try { id.close() } catch (ignored: Exception) {}
                    try { Thread.sleep(100) } catch (ignored: Exception) {}
                    continue
                }

                handler.post{ result.success(res) }
                success = true
                unregister()
                try { id.close() } catch (ignored: Exception) {}
                break

            } catch (e: Exception) {
               // Treat SecurityException "Tag ... is out of date" like a lost tag
               if (e is SecurityException) {
                   break
               }
               // If tag is lost, break to allow rediscovery.
               if (e is android.nfc.TagLostException || e.cause is android.nfc.TagLostException) {
                   break
               }

               // Slight delay before retry
               try { Thread.sleep(100) } catch (ignored: Exception) {}
               try { id?.close() } catch (ignored: Exception) {}
            } finally {
                if (success) {
                    try { id?.close() } catch (ignored: Exception) {}
                }
            }
        }
    }
}