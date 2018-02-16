package com.github.takuji31.realmgen

import java.io.Writer
import java.util.function.Function

data class PlatformPair<out T>(
    val ios: T,
    val android: T = ios
)

enum class Language(val fileName: String) {
    Swift("default_swift.mustache"), Kotlin("default_kotlin.mustache")
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
    var moduleName: String? = null

    val modelAndCommas: List<CommaPair<Model>>
        get() {
            return models.mapIndexed { index, model ->
                CommaPair(model, if (models.lastIndex != index) "," else null)
            }
        }

    fun model(name: String, block: Model.() -> Unit): Model {
        val model = Model(this, name)
        models += model
        return model.apply(block)
    }

    fun writeTo(writer: Writer, language: Language, fileName: String? = null, customFunctions: Map<String, Function<String, String>> = emptyMap()) {
        val mf = CodeGenMustacheFactory()
        val mustache = mf.compile(fileName ?: language.fileName)
        mustache.execute(writer, arrayOf(this, customFunctions)).flush()

    }
}

class Model(val schema: Schema, val name: String) {
    var properties: List<Property> = listOf()
        private set
    val propertyAndComma: List<CommaPair<Property>>
        get() {
            return properties.mapIndexed { index, property ->
                CommaPair(property, if (properties.lastIndex != index) "," else null)
            }
        }

    val initializerPropertyAndCommas: List<CommaPair<Property>>
        get() {
            return properties
                .filter { !it.isLinkingObjects }
                .mapIndexed { index, property ->
                    CommaPair(property, if (properties.lastIndex != index) "," else null)
                }
        }

    var primaryKey: Property? = null

    private val indexedProperties: List<Property>
        get() = properties.filter { it.indexed }

    val hasIndexedProperties: Boolean
        get() = indexedProperties.isNotEmpty()

    val indexedPropertiesString: String
        get() = indexedProperties.joinToString(separator = ", ", transform = { "\"${it.name}\"" })

    fun string(name: String, block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(name, FieldType.String, block)
    }

    fun int(name: String, block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(name, FieldType.Int, block)
    }

    fun long(name: String, block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(name, FieldType.Long, block)
    }

    fun bool(name: String, block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(name, FieldType.Bool, block)
    }

    fun float(name: String, block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(name, FieldType.Float, block)
    }

    fun double(name: String, block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(name, FieldType.Float, block)
    }

    fun date(name: String, block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(name, FieldType.Date, block)
    }

    fun data(name: String, block: TypeProperty.() -> Unit): TypeProperty {
        return createAndAddProperty(name, FieldType.Data, block)
    }

    private fun createAndAddProperty(name: String, fieldType: FieldType, block: TypeProperty.() -> Unit): TypeProperty {
        val property = TypeProperty(this, name, fieldType)
        properties += property
        return property.apply(block)
    }

    fun obj(
        name: String,
        target: Model,
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

class RelationList(val model: Model, override val name: String, val target: Model) : Property {
    override val isRelation: Boolean
        get() = true
    override val isListRelation: Boolean
        get() = true

    override fun value(): PlatformPair<String> {
        return PlatformPair(
            ios = "let $name = List<${target.name}>()",
            android = "var $name: RealmList<${target.name}> = RealmList()"
        )
    }
}

class RelationObject(val model: Model, override val name: String, val target: Model) : Property {
    override val isRelation: Boolean
        get() = true

    override fun value(): PlatformPair<String> {
        return PlatformPair(
            ios = "@objc dynamic var $name: ${target.name}?",
            android = "var $name: ${target.name}? = null"
        )
    }
}

class LinkingObjects(
    val model: Model,
    override val name: String,
    val target: Model,
    val property: Property,
    val isPrivate: Boolean = false
) : Property {
    override val isLinkingObjects: Boolean
        get() = true

    override fun value(): PlatformPair<String> {
        val privateModifier = if (isPrivate) "private " else ""
        return PlatformPair(
            ios = "${privateModifier}let $name = LinkingObjects(fromType: ${target.name}.self, property: \"${property.name}\")",
            android = "@LinkingObjects(\"${property.name}\") ${privateModifier}val $name: RealmResults<${target.name}>? = null"
        )
    }
}

class TypeProperty(val model: Model, override val name: String, val type: FieldType) : Property {
    var required = false
    override var indexed = false

    val isPrimaryKey: Boolean
        get() = model.primaryKey == this

    override val primaryKeyAnnotation: String?
        get() = if (isPrimaryKey) "@PrimaryKey " else ""

    override fun value(): PlatformPair<String> {
        val optionalMarker = if (required) "" else "?"
        return when (type) {
            FieldType.String, FieldType.Date, FieldType.Data -> {
                // Supports Nullable/Optional
                val defaultValue = if (required) type.defaultValue else PlatformPair(ios = "nil", android = "null")
                PlatformPair(
                    ios = "@objc dynamic var $name: ${type.typeName.ios}$optionalMarker = ${defaultValue.ios}",
                    android = "var $name: ${type.typeName.android}$optionalMarker = ${defaultValue.android}"
                )
            }
            FieldType.Int, FieldType.Long, FieldType.Bool, FieldType.Float, FieldType.Double -> {
                if (required) {
                    PlatformPair(
                        ios = "@objc dynamic var $name: ${type.typeName.ios} = ${type.defaultValue.ios}",
                        android = "var $name: ${type.typeName.android} = ${type.defaultValue.android}"
                    )
                } else {
                    PlatformPair(
                        ios = "let $name = RealmOptional<${type.typeName.ios}>()",
                        android = "var $name: ${type.typeName.android}? = null"
                    )
                }
            }
        }
    }
}

interface Property {
    val name: String
    val primaryKeyAnnotation: String?
        get() = ""
    val indexed: Boolean
        get() = false

    fun value(): PlatformPair<String>
    val isListRelation: Boolean
        get() = false
    val isRelation: Boolean
        get() = false
    val isLinkingObjects: Boolean
        get() = false
}


fun realmSchema(block: Schema.() -> Unit): Schema {
    return Schema().apply(block)
}
