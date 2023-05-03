package amadeus.maho.lang.javac.resource;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ArrayHelper;

@TransformProvider
class compiler {
    
    private static Object[][] injectContents() = {
            { "amadeus.maho.lang.err.reference.mark.nesting", "Reference mark nesting" },
            { "amadeus.maho.lang.err.delegate.method.must.have.no.parameters", "The delegate method {0} must have no parameters" },
            { "amadeus.maho.lang.err.getter.method.lazy", "The @Getter marked on the method does not support the 'lazy' attribute" },
            { "amadeus.maho.lang.err.accessor.method.has-parameter", "The methods marked by {0} must have no parameters" },
            { "amadeus.maho.lang.err.accessor.method.non-interface", "The method marked by {0} must be in the interface scope" },
            { "amadeus.maho.lang.err.accessor.method.static", "The method marked by {0} must be non-static" },
            { "amadeus.maho.lang.err.runtime.missing.type", "Type {0} is the missing type at runtime" },
            { "amadeus.maho.lang.err.type-token.2nd.must-be.parameterized-type", "The second type parameter must be a parameterized type" },
            { "amadeus.maho.lang.err.type-token.missing.type-arg", "Missing compile-time type parameter" },
            { "amadeus.maho.lang.err.doesnt.exist", "{0} does not exist" },
            { "amadeus.maho.lang.err.inconvertible.types", "{0} cannot be converted to {1}" },
            { "amadeus.maho.lang.err.address.of.type", "Type {0} cannot be copied to off-heap memory" },
            { "amadeus.maho.lang.err.resource.agent.missing.key", "The following group with the same name as the parameter declared in the method is missing from the resource agent's regular expression: {0}" },
            { "amadeus.maho.lang.err.resource.agent.invalid.parameter", "Invalid parameter type, must be string type" },
            { "amadeus.maho.lang.err.resource.bundle.agent.repeat", "Unexpected duplication of the path regular expression for the method in which the resource agent is located.\n  {0}\n  {1}" },
            { "amadeus.maho.lang.err.resource.bundle.ioe", "An IO exception occurred when visiting the path corresponding to the ResourceBundle: {0}" },
            { "amadeus.maho.lang.err.resource.bundle.repeatedly.matched", "The same path is repeatedly matched by multiple regular expressions.\npath: {0}\n  {1}" },
            { "amadeus.maho.lang.err.rearrange.target.must.be.record", "The target of @Rearrange must be record" },
            { "amadeus.maho.lang.err.rearrange.target.invalid.fields.length", "The target of @Rearrange must have at least two fields" },
            { "amadeus.maho.lang.err.rearrange.target.invalid.fields.alisa", "Invalid alias for @Rearrange" },
            { "amadeus.maho.lang.err.rearrange.components.must.be.same", "The components of @Rearrange must be of the same type" },
            { "amadeus.maho.lang.err.rearrange.adapters.components.must.be.same", "The components of @Rearrange adapter must be of the same type" },
            { "amadeus.maho.lang.err.value.based.compare.inconsistency", "Comparison expressions of value types must be of the same type left and right.\nleft: {0}\nright{1}" },
    };
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Object[][] getContents(final Object result[][], final com.sun.tools.javac.resources.compiler $this) = ArrayHelper.addAll(result, injectContents());
    
}
