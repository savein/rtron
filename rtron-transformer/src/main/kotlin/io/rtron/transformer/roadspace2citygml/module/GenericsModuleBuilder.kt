/*
 * Copyright 2019-2020 Chair of Geoinformatics, Technical University of Munich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rtron.transformer.roadspace2citygml.module

import com.github.kittinunf.result.Result
import io.rtron.io.logging.Logger
import io.rtron.math.geometry.euclidean.threed.AbstractGeometry3D
import io.rtron.math.geometry.euclidean.threed.curve.AbstractCurve3D
import io.rtron.std.handleFailure
import io.rtron.transformer.roadspace2citygml.geometry.GeometryTransformer
import io.rtron.transformer.roadspace2citygml.parameter.Roadspaces2CitygmlConfiguration
import io.rtron.transformer.roadspace2citygml.transformer.AttributesAdder
import org.citygml4j.model.citygml.generics.GenericCityObject


/**
 * Builder for city objects of the CityGML Generics module.
 */
class GenericsModuleBuilder(
        val configuration: Roadspaces2CitygmlConfiguration
) {

    // Properties and Initializers
    private val _reportLogger: Logger = configuration.getReportLogger()
    private val _attributesAdder = AttributesAdder(configuration.parameters)

    // Methods
    fun createGenericObject(curve3D: AbstractCurve3D): Result<GenericCityObject, Exception> {
        val geometryTransformer = GeometryTransformer(configuration.parameters, _reportLogger)
                .also { curve3D.accept(it) }
        return createGenericObject(geometryTransformer)
    }

    fun createGenericObject(abstractGeometry3D: AbstractGeometry3D): Result<GenericCityObject, Exception> {
        val geometryTransformer = GeometryTransformer(configuration.parameters, _reportLogger)
                .also { abstractGeometry3D.accept(it) }
        return createGenericObject(geometryTransformer)
    }

    fun createGenericObject(geometryTransformer: GeometryTransformer): Result<GenericCityObject, Exception> {
        val genericCityObject = GenericCityObject()
        genericCityObject.lod2Geometry = geometryTransformer.getGeometryProperty()
                .handleFailure { return it }
        if (geometryTransformer.isSetRotation())
            _attributesAdder.addRotationAttributes(geometryTransformer.rotation, genericCityObject)
        return Result.success(genericCityObject)
    }
}
