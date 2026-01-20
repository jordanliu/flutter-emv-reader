package com.paytrek.stater

import android.nfc.tech.IsoDep
import android.nfc.TagLostException
import android.util.Log
import com.github.devnied.emvnfccard.parser.IProvider
import java.io.IOException

class IsoDepProvider(val tag: IsoDep): IProvider {
    override fun transceive(command: ByteArray): ByteArray {
        // We do not catch exceptions here anymore. We let them propagate to NfcScanner/NfcReader
        // so that the entire card reading process (connection + select + read) can be retried.
        return tag.transceive(command)
    }

    override fun getAt(): ByteArray {
        return tag.getHistoricalBytes()
    }
} 