package net.msrandom.minecraftcodev.remapper.extra.kotlin

import kotlinx.metadata.ExperimentalContextReceivers
import kotlinx.metadata.KmClass
import org.objectweb.asm.commons.Remapper
import kotlinx.metadata.ClassName
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFlexibleTypeUpperBound
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmLambda
import kotlinx.metadata.KmPackage
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeAlias
import kotlinx.metadata.KmTypeParameter
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.isLocalClassName
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.annotations
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.localDelegatedProperties
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.jvm.syntheticMethodForAnnotations
import kotlinx.metadata.jvm.syntheticMethodForDelegate
import kotlinx.metadata.jvm.toJvmInternalName

@OptIn(ExperimentalContextReceivers::class)
class KotlinMetadataRemapper(private val remapper: Remapper) {
    fun remap(clazz: KmClass): KmClass {
        clazz.name = remap(clazz.name)
        clazz.typeParameters.replaceAll(this::remap)
        clazz.supertypes.replaceAll(this::remap)
        clazz.functions.replaceAll(this::remap)
        clazz.properties.replaceAll(this::remap)
        clazz.typeAliases.replaceAll(this::remap)
        clazz.constructors.replaceAll(this::remap)
        clazz.nestedClasses.replaceAll(this::remap)
        clazz.sealedSubclasses.replaceAll(this::remap)
        clazz.contextReceiverTypes.replaceAll(this::remap)
        clazz.localDelegatedProperties.replaceAll(this::remap)
        return clazz
    }

    fun remap(lambda: KmLambda): KmLambda {
        lambda.function = remap(lambda.function)
        return lambda
    }

    fun remap(pkg: KmPackage): KmPackage {
        pkg.functions.replaceAll(this::remap)
        pkg.properties.replaceAll(this::remap)
        pkg.typeAliases.replaceAll(this::remap)
        pkg.localDelegatedProperties.replaceAll(this::remap)
        return pkg
    }

    private fun remap(name: ClassName): ClassName {
        val local = name.isLocalClassName()
        val remapped = remapper.map(name.toJvmInternalName()).replace('$', '.')

        if (local) {
            return ".$remapped"
        }

        return remapped
    }

    private fun remap(type: KmType): KmType {
        type.classifier =
            when (val classifier = type.classifier) {
                is KmClassifier.Class -> KmClassifier.Class(remap(classifier.name))
                is KmClassifier.TypeParameter -> KmClassifier.TypeParameter(classifier.id)
                is KmClassifier.TypeAlias -> KmClassifier.TypeAlias(remap(classifier.name))
            }
        type.arguments.replaceAll(this::remap)
        type.abbreviatedType = type.abbreviatedType?.let { remap(it) }
        type.outerType = type.outerType?.let { remap(it) }
        type.flexibleTypeUpperBound = type.flexibleTypeUpperBound?.let { remap(it) }
        type.annotations.replaceAll(this::remap)
        return type
    }

    private fun remap(function: KmFunction): KmFunction {
        function.typeParameters.replaceAll(this::remap)
        function.receiverParameterType = function.receiverParameterType?.let { remap(it) }
        function.contextReceiverTypes.replaceAll(this::remap)
        function.valueParameters.replaceAll(this::remap)
        function.returnType = remap(function.returnType)
        function.signature = function.signature?.let { remap(it) }
        return function
    }

    private fun remap(property: KmProperty): KmProperty {
        property.typeParameters.replaceAll(this::remap)
        property.receiverParameterType = property.receiverParameterType?.let { remap(it) }
        property.contextReceiverTypes.replaceAll(this::remap)
        property.setterParameter = property.setterParameter?.let { remap(it) }
        property.returnType = remap(property.returnType)
        property.fieldSignature = property.fieldSignature?.let { remap(it) }
        property.getterSignature = property.getterSignature?.let { remap(it) }
        property.setterSignature = property.setterSignature?.let { remap(it) }
        property.syntheticMethodForAnnotations = property.syntheticMethodForAnnotations?.let { remap(it) }
        property.syntheticMethodForDelegate = property.syntheticMethodForDelegate?.let { remap(it) }
        return property
    }

    private fun remap(typeAlias: KmTypeAlias): KmTypeAlias {
        typeAlias.typeParameters.replaceAll(this::remap)
        typeAlias.underlyingType = remap(typeAlias.underlyingType)
        typeAlias.expandedType = remap(typeAlias.expandedType)
        typeAlias.annotations.replaceAll(this::remap)
        return typeAlias
    }

    private fun remap(constructor: KmConstructor): KmConstructor {
        constructor.valueParameters.replaceAll(this::remap)
        constructor.signature = constructor.signature?.let { remap(it) }
        return constructor
    }

    private fun remap(typeParameter: KmTypeParameter): KmTypeParameter {
        typeParameter.upperBounds.replaceAll(this::remap)
        typeParameter.annotations.replaceAll(this::remap)
        return typeParameter
    }

    private fun remap(typeProjection: KmTypeProjection) =
        KmTypeProjection(typeProjection.variance, typeProjection.type?.let { remap(it) })

    private fun remap(flexibleTypeUpperBound: KmFlexibleTypeUpperBound) =
        KmFlexibleTypeUpperBound(remap(flexibleTypeUpperBound.type), flexibleTypeUpperBound.typeFlexibilityId)

    private fun remap(valueParameter: KmValueParameter): KmValueParameter {
        valueParameter.type = remap(valueParameter.type)
        valueParameter.varargElementType = valueParameter.varargElementType?.let { remap(it) }
        return valueParameter
    }

    private fun remap(annotation: KmAnnotation) = KmAnnotation(remap(annotation.className), annotation.arguments)
    private fun remap(signature: JvmMethodSignature) = JvmMethodSignature(signature.name, remapper.mapMethodDesc(signature.descriptor))
    private fun remap(signature: JvmFieldSignature) = JvmFieldSignature(signature.name, remapper.mapDesc(signature.descriptor))
}
