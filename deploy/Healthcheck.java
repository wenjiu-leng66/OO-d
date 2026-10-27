import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 使用 JRE 自带 HTTP 客户端检测就绪，无需在运行镜像安装 curl。
 * 调用方传入本机 /api/v1/health；只接受 HTTP 200，连接/读取异常直接以非零进程状态失败。
 * 探针不读取响应体，也不验证业务权限；这些检查由真实 HTTP 验收脚本负责。
 */
public final class Healthcheck {
    public static void main(String[] args) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(args[0]))
                .timeout(Duration.ofSeconds(3)).GET().build();
        var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                .send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) System.exit(1);
    }
}
