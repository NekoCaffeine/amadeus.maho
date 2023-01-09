package amadeus.maho.transform.handler;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.MethodTransformer;
import amadeus.maho.transform.mark.Web;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.link.http.HttpHelper;

import static amadeus.maho.util.link.http.HttpHelper.make;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class WebRequestTransformer extends MethodTransformer<Web.Request> implements ClassTransformer.Limited {
    
    private static final Type
            TYPE_HTTP_HELPER          = Type.getType(HttpHelper.class),
            TYPE_HTTP_CLIENT          = Type.getType(HttpClient.class),
            TYPE_HTTP_REQUEST         = Type.getType(HttpRequest.class),
            TYPE_HTTP_REQUEST_BUILDER = Type.getType(HttpRequest.Builder.class),
            TYPE_BODY_PUBLISHER       = Type.getType(HttpRequest.BodyPublisher.class),
            TYPE_HTTP_RESPONSE        = Type.getType(HttpResponse.class),
            TYPE_BODY_HANDLER         = Type.getType(HttpResponse.BodyHandler.class),
            TYPE_PUSH_PROMISE_HANDLER = Type.getType(HttpResponse.PushPromiseHandler.class),
            TYPE_COMPLETABLE_FUTURE   = Type.getType(CompletableFuture.class);
    
    private static final Method
            contextHttpClient = { "contextHttpClient", Type.getMethodDescriptor(TYPE_HTTP_CLIENT, ASMHelper.TYPE_STRING) },
            method            = { "method", Type.getMethodDescriptor(TYPE_HTTP_REQUEST_BUILDER, ASMHelper.TYPE_STRING, TYPE_BODY_PUBLISHER) },
            build             = { "build", Type.getMethodDescriptor(TYPE_HTTP_REQUEST) },
            send              = { "send", Type.getMethodDescriptor(TYPE_HTTP_RESPONSE, TYPE_HTTP_REQUEST, TYPE_BODY_HANDLER) },
            sendAsync         = { "sendAsync", Type.getMethodDescriptor(TYPE_COMPLETABLE_FUTURE, TYPE_HTTP_REQUEST, TYPE_BODY_HANDLER) },
            sendAsyncPromise  = { "sendAsync", Type.getMethodDescriptor(TYPE_COMPLETABLE_FUTURE, TYPE_HTTP_REQUEST, TYPE_BODY_HANDLER, TYPE_PUSH_PROMISE_HANDLER) };
    
    Type argsTypes[] = Type.getArgumentTypes(sourceMethod.desc), returnType = Type.getReturnType(sourceMethod.desc);
    int
            candidateHttpClientIndex         = ASMHelper.findCandidateParameter(argsTypes, TYPE_HTTP_CLIENT),
            candidateBodyPublisherIndex      = ASMHelper.findCandidateParameter(argsTypes, TYPE_BODY_PUBLISHER),
            candidateBodyHandlerIndex        = ASMHelper.findCandidateParameter(argsTypes, TYPE_BODY_HANDLER),
            candidatePushPromiseHandlerIndex = ASMHelper.findCandidateParameter(argsTypes, TYPE_PUSH_PROMISE_HANDLER);
    boolean async = returnType.equals(TYPE_COMPLETABLE_FUTURE);
    
    @Override
    public ClassNode doTransform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        for (final MethodNode methodNode : node.methods)
            if (methodNode.name.equals(sourceMethod.name) && methodNode.desc.equals(sourceMethod.desc)) {
                TransformerManager.transform("web.request", "%s#%s%s".formatted(ASMHelper.sourceName(node.name), methodNode.name, methodNode.desc));
                ASMHelper.rollback(methodNode, contextMethodNode -> {
                    final MethodGenerator generator = MethodGenerator.fromMethodNode(contextMethodNode);
                    if (candidateHttpClientIndex > -1)
                        generator.loadArg(candidateHttpClientIndex); // HttpClient
                    else {
                        generator.push(annotation.context()); // String
                        generator.invokeStatic(TYPE_HTTP_HELPER, contextHttpClient, true); // HttpClient
                    }
                    final Type methodType = Type.getMethodType(methodNode.desc);
                    if (candidateBodyPublisherIndex > -1) {
                        generator.push(new ConstantDynamic("builder", TYPE_HTTP_REQUEST_BUILDER.getDescriptor(), make, methodNode.name, methodType)); // HttpClient, HttpRequest.Builder
                        generator.push(annotation.method()); // HttpClient, HttpRequest.Builder, String
                        generator.loadArg(candidateBodyPublisherIndex); // HttpClient, HttpRequest.Builder, String, BodyPublisher
                        generator.invokeVirtual(TYPE_HTTP_REQUEST_BUILDER, method); // HttpClient, HttpRequest.Builder
                        generator.invokeVirtual(TYPE_HTTP_REQUEST_BUILDER, build); // HttpClient, HttpRequest
                    } else
                        generator.push(new ConstantDynamic("request", TYPE_HTTP_REQUEST.getDescriptor(), make, methodNode.name, methodType)); // HttpClient, HttpRequest
                    if (candidateBodyHandlerIndex > -1)
                        generator.loadArg(candidateBodyHandlerIndex);
                    if (candidatePushPromiseHandlerIndex > -1) {
                        generator.loadArg(candidatePushPromiseHandlerIndex); // HttpClient, HttpRequest, PushPromiseHandler
                        generator.invokeVirtual(TYPE_HTTP_CLIENT, sendAsyncPromise); // CompletableFuture<HttpResponse>
                    } else
                        generator.invokeVirtual(TYPE_HTTP_CLIENT, async ? sendAsync : send); // CompletableFuture<HttpResponse> | HttpResponse
                    generator.returnValue();
                });
                context.markCompute(methodNode, ComputeType.MAX, ComputeType.FRAME);
                break;
            }
        return node;
    }
    
    @Override
    public Set<String> targets() = Set.of(ASMHelper.sourceName(sourceClass.name));
    
}
