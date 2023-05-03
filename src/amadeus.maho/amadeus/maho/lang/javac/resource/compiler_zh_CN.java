package amadeus.maho.lang.javac.resource;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ArrayHelper;

@TransformProvider
class compiler_zh_CN {
    
    private static Object[][] injectContents() = {
            { "amadeus.maho.lang.err.reference.mark.nesting", "引用标记嵌套" },
            { "amadeus.maho.lang.err.delegate.method.must.have.no.parameters", "委托方法 {0} 必须没有参数" },
            { "amadeus.maho.lang.err.getter.method.lazy", "@Getter 标记的方法不支持 'lazy' 属性" },
            { "amadeus.maho.lang.err.accessor.method.has-parameter", "{0} 标记的方法必须没有参数" },
            { "amadeus.maho.lang.err.accessor.method.non-interface", "{0} 标记的方法必须在 interface 域内" },
            { "amadeus.maho.lang.err.accessor.method.static", "{0} 标记的方法必须是非静态的" },
            { "amadeus.maho.lang.err.runtime.missing.type", "{0} 是运行时缺少的类型" },
            { "amadeus.maho.lang.err.type-token.2nd.must-be.parameterized-type", "第二个类型参数必须是参数化类型" },
            { "amadeus.maho.lang.err.type-token.missing.type-arg", "缺少编译期类型参数" },
            { "amadeus.maho.lang.err.doesnt.exist", "{0} 不存在" },
            { "amadeus.maho.lang.err.inconvertible.types", "{0} 无法转换为 {1}" },
            { "amadeus.maho.lang.err.address.of.type", "类型 {0} 无法被复制到堆外内存" },
            { "amadeus.maho.lang.err.resource.agent.missing.key", "ResourceAgent 的正则表达式中缺少以下在方法中声明的参数的同名组: {0}" },
            { "amadeus.maho.lang.err.resource.agent.invalid.parameter", "无效的参数类型，必须是字符串类型" },
            { "amadeus.maho.lang.err.resource.bundle.agent.repeat", "ResourceAgent 所在方法的路径正则表达式意外的重复\n  {0}\n  {1}" },
            { "amadeus.maho.lang.err.resource.bundle.ioe", "访问 ResourceBundle 对应路径时发生 IO 异常: {0}" },
            { "amadeus.maho.lang.err.resource.bundle.ioe", "同一路径被多个正则表达式重复匹配s.\n路径: {0}\n  {1}" },
            { "amadeus.maho.lang.err.rearrange.target.must.be.record", "@Rearrange 的目标必须是 record" },
            { "amadeus.maho.lang.err.rearrange.target.invalid.fields.length", "@Rearrange 的目标必须至少有两个字段" },
            { "amadeus.maho.lang.err.rearrange.target.invalid.fields.alisa", "@Rearrange 的别名无效" },
            { "amadeus.maho.lang.err.rearrange.components.must.be.same", "@Rearrange 的组分必须类型一致" },
            { "amadeus.maho.lang.err.rearrange.adapters.components.must.be.same", "@Rearrange 的适配器的组分必须类型一致" },
            { "amadeus.maho.lang.err.value.based.compare.inconsistency", "值类型的比较表达式左右类型必须一致\n左： {0}\n右： {1}" },
    };
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Object[][] getContents(final Object result[][], final com.sun.tools.javac.resources.compiler_zh_CN $this) = ArrayHelper.addAll(result, injectContents());
    
}
