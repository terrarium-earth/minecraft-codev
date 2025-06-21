package net.msrandom.minecraftcodev.remapper.extra.kotlin

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.AnnotationNode
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlin.reflect.jvm.javaGetter

class KotlinMetadataRemappingAnnotationVisitor(
    private val remapper: Remapper,
    private val next: AnnotationVisitor,
) : AnnotationVisitor(Opcodes.ASM9, null) {
    private val node = AnnotationNode(Opcodes.ASM9, KotlinMetadataRemappingClassVisitor.ANNOTATION_DESCRIPTOR)

    override fun visit(name: String?, value: Any?) =
        node.visit(name, value)

    override fun visitAnnotation(name: String, descriptor: String): AnnotationVisitor? =
        node.visitAnnotation(name, descriptor)

    override fun visitArray(name: String): AnnotationVisitor? =
        node.visitArray(name)

    override fun visitEnum(name: String, descriptor: String, value: String) =
        node.visitEnum(name, descriptor, value)

    override fun visitEnd() {
        node.visitEnd()

        val header = readMetadataAnnotation() ?: return

        val metadata = KotlinClassMetadata.readLenient(header)

        if (metadata.version.major < 1 || (metadata.version.major == 1 && metadata.version.minor < 4)) {
            node.accept(next)
            return
        }

        when (metadata) {
            is KotlinClassMetadata.Class -> {
                var klass = metadata.kmClass
                klass = KotlinMetadataRemapper(remapper).remap(klass)

                val remapped = KotlinClassMetadata.Class(klass, metadata.version, metadata.flags).write()
                writeMetadataAnnotationValues(remapped)

                node.accept(next)
            }
            is KotlinClassMetadata.SyntheticClass -> {
                var klambda = metadata.kmLambda

                if (klambda != null) {
                    klambda = KotlinMetadataRemapper(remapper).remap(klambda)

                    val remapped = KotlinClassMetadata.SyntheticClass(klambda, metadata.version, metadata.flags).write()
                    writeMetadataAnnotationValues(remapped)
                }

                node.accept(next)
            }
            is KotlinClassMetadata.FileFacade -> {
                var kpackage = metadata.kmPackage

                kpackage = KotlinMetadataRemapper(remapper).remap(kpackage)

                val remapped = KotlinClassMetadata.FileFacade(kpackage, metadata.version, metadata.flags).write()
                writeMetadataAnnotationValues(remapped)

                node.accept(next)
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                var kpackage = metadata.kmPackage

                kpackage = KotlinMetadataRemapper(remapper).remap(kpackage)

                val remapped = KotlinClassMetadata.MultiFileClassPart(
                    kpackage,
                    metadata.facadeClassName,
                    metadata.version,
                    metadata.flags,
                ).write()

                writeMetadataAnnotationValues(remapped)

                node.accept(next)
            }
            is KotlinClassMetadata.MultiFileClassFacade, is KotlinClassMetadata.Unknown -> {
                // do nothing
                node.accept(next)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readMetadataAnnotation(): Metadata? {
        var kind: Int? = null
        lateinit var metadataVersion: IntArray
        lateinit var bytecodeVersion: IntArray
        lateinit var data1: Array<String>
        lateinit var data2: Array<String>
        lateinit var extraString: String
        lateinit var packageName: String
        var extraInt: Int? = null

        if (node.values == null) {
            return null
        }

        for ((name, value) in node.values.chunked(2)) {
            when (name) {
                kindPropertyName -> kind = value as Int
                metadataVersionPropertyName -> metadataVersion = (value as List<Int>).toIntArray()
                bytecodeVersionPropertyName -> bytecodeVersion = (value as List<Int>).toIntArray()
                data1PropertyName -> data1 = (value as List<String>).toTypedArray()
                data2PropertyName -> data2 = (value as List<String>).toTypedArray()
                extraStringPropertyName -> extraString = value as String
                packageNamePropertyName -> packageName = value as String
                extraIntPropertyName -> extraInt = value as Int
            }
        }

        return Metadata(kind!!, metadataVersion, bytecodeVersion, data1, data2, extraString, packageName, extraInt!!)
    }

    private fun writeMetadataAnnotationValues(header: Metadata) {
        if (node.values == null) {
            node.values = mutableListOf()
        }

        for ((keyIndex, valueIndex) in node.values.indices.chunked(2)) {
            when (node.values[keyIndex]) {
                kindPropertyName -> node.values[valueIndex] = header.kind
                metadataVersionPropertyName -> node.values[valueIndex] = header.metadataVersion.toList()
                bytecodeVersionPropertyName -> node.values[valueIndex] = header.bytecodeVersion.toList()
                data1PropertyName -> node.values[valueIndex] = header.data1.toList()
                data2PropertyName -> node.values[valueIndex] = header.data2.toList()
                extraStringPropertyName -> node.values[valueIndex] = header.extraString
                packageNamePropertyName -> node.values[valueIndex] = header.packageName
                extraIntPropertyName -> node.values[valueIndex] = header.extraInt
            }
        }
    }

    private companion object {
        val kindPropertyName: String = Metadata::kind.javaGetter!!.name
        val metadataVersionPropertyName: String = Metadata::metadataVersion.javaGetter!!.name
        val bytecodeVersionPropertyName: String = Metadata::bytecodeVersion.javaGetter!!.name
        val data1PropertyName: String = Metadata::data1.javaGetter!!.name
        val data2PropertyName: String = Metadata::data2.javaGetter!!.name
        val extraStringPropertyName: String = Metadata::extraString.javaGetter!!.name
        val packageNamePropertyName: String = Metadata::packageName.javaGetter!!.name
        val extraIntPropertyName: String = Metadata::extraInt.javaGetter!!.name
    }
}
