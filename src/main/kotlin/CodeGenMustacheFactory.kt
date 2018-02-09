package com.github.takuji31.realmgen

import com.github.mustachejava.DefaultMustacheFactory
import java.io.Writer

class CodeGenMustacheFactory : DefaultMustacheFactory() {
    override fun encode(value: String?, writer: Writer?) {
        writer?.write(value)
    }
}
