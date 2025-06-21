package net.msrandom.minecraftcodev.remapper.extra.kotlin

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper

// Copied from fabric-loom
class KotlinMetadataRemappingClassVisitor(private val remapper: Remapper, next: ClassVisitor?) : ClassVisitor(Opcodes.ASM9, next) {
    companion object {
        val ANNOTATION_DESCRIPTOR: String = Type.getDescriptor(Metadata::class.java)
    }

    var className: String? = null

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        this.className = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor? {
        var visitor: AnnotationVisitor? = super.visitAnnotation(descriptor, visible)

        if (descriptor == ANNOTATION_DESCRIPTOR && visitor != null) {
            return KotlinMetadataRemappingAnnotationVisitor(remapper, visitor)
        }

        return visitor
    }
}
