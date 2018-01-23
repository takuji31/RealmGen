package com.github.takuji31.realmmodelgenerator

import com.github.mustachejava.DefaultMustacheFactory
import java.io.PrintWriter
import java.io.Writer
import kotlin.properties.Delegates

data class PlatformPair<out T>(
    val ios: T,
    val android: T = ios
)

enum class Language(val fileName: String) {
    Swift("swift.mustache"), Kotlin("kotlin.mustache")
}

enum class FieldType(val typeName: PlatformPair<kotlin.String>, val defaultValue: PlatformPair<kotlin.String>) {
    String(PlatformPair(ios = "String"), PlatformPair(ios = "\"\"", android = "\"\"")),
    Int(PlatformPair(ios = "Int32", android = "Int"), PlatformPair(ios = "0", android = "0")),
    Long(PlatformPair(ios = "Int64", android = "Long"), PlatformPair(ios = "0", android = "0L")),
    Bool(PlatformPair(ios = "Bool", android = "Boolean"), PlatformPair(ios = "false", android = "false")),
    Float(PlatformPair(ios = "Float"), PlatformPair(ios = "0.0f", android = "0.0f")),
    Double(PlatformPair(ios = "Double"), PlatformPair(ios = "0.0", android = "0.0")),
    Date(PlatformPair(ios = "Date"), PlatformPair(ios = "Date()", android = "Date()")),
    Data(PlatformPair(ios = "Data", android = "ByteArray"), PlatformPair(ios = "Data()", android = "byteArrayOf()"));
}

data class CommaPair<T>(val data: T, val comma: String? = null)

class Schema {
    var packageName: String = ""
    var models: List<Model> = listOf()
        private set

    fun model(block: Model.() -> Unit): Model {
        val model = Model(this)
        models += model
        return model.apply(block)
    }

    fun writeTo(writer: Writer, language: Language) {
        val mf = DefaultMustacheFactory()
        val mustache = mf.compile(language.fileName)
        mustache.execute(PrintWriter(System.out), this).flush()

    }
}

class Model(val schema: Schema) {
    var name: String by Delegates.notNull()
    var properties: List<Property> = listOf()
        private set
    val propertyAndComma: List<CommaPair<Property>>
        get() {
            return properties.mapIndexed { index, property ->
                CommaPair(property, if (properties.lastIndex != index) "," else null)
            }
        }

    var primaryKey: Property? = null

    fun string(block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(FieldType.String, block)
    }

    fun int(block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(FieldType.Int, block)
    }

    fun long(block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(FieldType.Long, block)
    }

    fun bool(block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(FieldType.Bool, block)
    }

    fun float(block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(FieldType.Float, block)
    }

    fun double(block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(FieldType.Float, block)
    }

    fun date(block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(FieldType.Date, block)
    }

    fun data(block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(FieldType.Data, block)
    }

    private fun createAndAddProperty(fieldType: FieldType, block: TypeProperty.() -> Unit): TypeProperty {
        val property = TypeProperty(this, fieldType)
        properties += property
        return property.apply(block)
    }

    fun obj(
        name: String, target: Model,
        createLinkingObjects: Boolean = false,
        linkingObjectsName: String? = null,
        linkingObjectsIsPrivate: Boolean = false
    ): RelationObject {
        val relationObject = RelationObject(this, name, target)
        properties += relationObject
        if (createLinkingObjects) {
            val linkingObjects = LinkingObjects(
                target,
                linkingObjectsName
                        ?: throw IllegalArgumentException("needs linkingObjectsName when creating LinkingObjects"),
                this,
                relationObject,
                linkingObjectsIsPrivate
            )
            target.properties += linkingObjects
        }
        return relationObject
    }

    fun list(
        name: String,
        target: Model,
        createLinkingObjects: Boolean = false,
        linkingObjectsName: String? = null,
        linkingObjectsIsPrivate: Boolean = false
    ): RelationList {
        val relationList = RelationList(this, name, target)
        properties += relationList
        if (createLinkingObjects) {
            val linkingObjects = LinkingObjects(
                target,
                linkingObjectsName
                        ?: throw IllegalArgumentException("needs linkingObjectsName when creating LinkingObjects"),
                this,
                relationList,
                linkingObjectsIsPrivate
            )
            target.properties += linkingObjects
        }
        return relationList
    }
}

class RelationList(val model: Model, override var name: String, val target: Model) : Property {
    override fun value(): PlatformPair<String> {
        return PlatformPair(
            ios = "let $name = List<${target.name}>()",
            android = "val $name = RealmList<${target.name}>()"
        )
    }
}

class RelationObject(val model: Model, override var name: String, val target: Model) : Property {
    override fun value(): PlatformPair<String> {
        return PlatformPair(
            ios = "@objc dynamic var $name: ${model.name}?",
            android = "var $name: ${model.name}? = null"
        )
    }
}

class LinkingObjects(
    val model: Model,
    override var name: String,
    val target: Model,
    val property: Property,
    val isPrivate: Boolean = false
) : Property {
    override fun value(): PlatformPair<String> {
        val privateModifier = if (isPrivate) "private " else ""
        return PlatformPair(
            ios = "${privateModifier}let $name = LinkingObjects(fromType: ${target.name}.self, property: \"${property.name}\")",
            android = "@LinkingObjects(\"${property.name}\") ${privateModifier}val $name: RealmResults<${target.name}>? = null"
        )
    }
}

class TypeProperty(val model: Model, val type: FieldType) : Property {
    override var name: String by Delegates.notNull()
    var required = false

    override fun value(): PlatformPair<String> {
        val optionalMarker = if (required) "" else "?"
        return when (type) {
            FieldType.String, FieldType.Date, FieldType.Data -> {
                // Supports Nullable/Optional
                val defaultValue = if (required) type.defaultValue else PlatformPair(ios = "nil", android = "null")
                PlatformPair(
                    ios = "@objc dynamic var $name: String$optionalMarker = ${defaultValue.ios}",
                    android = "var $name: String$optionalMarker = ${defaultValue.android}"
                )
            }
            FieldType.Int, FieldType.Long, FieldType.Bool, FieldType.Float, FieldType.Double -> {
                val defaultValue = if (required) type.defaultValue else PlatformPair(
                    ios = "RealmOptional<${type.typeName.ios}>()",
                    android = "null"
                )
                PlatformPair(
                    ios = "let $name = ${defaultValue.ios}",
                    android = "var $name: ${type.typeName.android}$optionalMarker = ${defaultValue.android}"
                )
            }
        }
    }
}

interface Property {
    var name: String
    fun value(): PlatformPair<String>
}


fun realmSchema(block: Schema.() -> Unit): Schema {
    return Schema().apply(block)
}