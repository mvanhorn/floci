package io.github.hectorvent.floci.services.s3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for virtual-hosted-style S3 requests.
 * Bucket name is sent via the Host header instead of the path.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3VirtualHostIntegrationTest {

    private static final String BUCKET = "vhost-bucket";
    private static final String HOST = BUCKET + ".localhost";

    private static final String REGION_BUCKET = "vhost-region-bucket";
    private static final String REGION_HOST = REGION_BUCKET + ".s3.us-east-1.localhost";

    private static final String PROXY_BUCKET = "vhost-proxy-bucket";
    private static final String PROXY_HOST = PROXY_BUCKET + ".localhost:4566";
    private static final String PROXY_KEY = "0123456789abcdef0123456789abcdef.json";

    @Test
    @Order(1)
    void createBucketViaVirtualHost() {
        given()
            .header("Host", HOST)
        .when()
            .put("/")
        .then()
            .statusCode(200)
            .header("Location", equalTo("/" + BUCKET));
    }

    @Test
    @Order(2)
    void headBucketViaVirtualHost() {
        given()
            .header("Host", HOST)
        .when()
            .head("/")
        .then()
            .statusCode(200)
            .header("x-amz-bucket-region", notNullValue());
    }

    @Test
    @Order(3)
    void putObjectViaVirtualHost() {
        given()
            .header("Host", HOST)
            .contentType("text/plain")
            .header("x-amz-meta-source", "virtual-host-test")
            .body("virtual hosted content")
        .when()
            .put("/hello.txt")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue());
    }

    @Test
    @Order(4)
    void getObjectViaVirtualHost() {
        given()
            .header("Host", HOST)
        .when()
            .get("/hello.txt")
        .then()
            .statusCode(200)
            .header("x-amz-meta-source", equalTo("virtual-host-test"))
            .body(equalTo("virtual hosted content"));
    }

    @Test
    @Order(5)
    void headObjectViaVirtualHost() {
        given()
            .header("Host", HOST)
        .when()
            .head("/hello.txt")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue())
            .header("Content-Length", notNullValue());
    }

    @Test
    @Order(6)
    void putObjectWithNestedKeyViaVirtualHost() {
        given()
            .header("Host", HOST)
            .contentType("application/json")
            .body("{\"nested\": true}")
        .when()
            .put("/path/to/nested.json")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(7)
    void listObjectsViaVirtualHost() {
        given()
            .header("Host", HOST)
        .when()
            .get("/")
        .then()
            .statusCode(200)
            .body(containsString("hello.txt"))
            .body(containsString("path/to/nested.json"));
    }

    @Test
    @Order(8)
    void listObjectsWithPrefixViaVirtualHost() {
        given()
            .header("Host", HOST)
            .queryParam("prefix", "path/")
        .when()
            .get("/")
        .then()
            .statusCode(200)
            .body(containsString("path/to/nested.json"))
            .body(not(containsString("hello.txt")));
    }

    @Test
    @Order(9)
    void copyObjectViaVirtualHost() {
        given()
            .header("Host", HOST)
            .header("x-amz-copy-source", "/" + BUCKET + "/hello.txt")
        .when()
            .put("/hello-copy.txt")
        .then()
            .statusCode(200)
            .body(containsString("CopyObjectResult"));

        given()
            .header("Host", HOST)
        .when()
            .get("/hello-copy.txt")
        .then()
            .statusCode(200)
            .body(equalTo("virtual hosted content"));
    }

    @Test
    @Order(10)
    void deleteObjectViaVirtualHost() {
        given()
            .header("Host", HOST)
        .when()
            .delete("/hello-copy.txt")
        .then()
            .statusCode(204);

        given()
            .header("Host", HOST)
        .when()
            .get("/hello-copy.txt")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(11)
    void getObjectNotFoundViaVirtualHost() {
        given()
            .header("Host", HOST)
        .when()
            .get("/nonexistent.txt")
        .then()
            .statusCode(404)
            .body(containsString("NoSuchKey"));
    }

    @Test
    @Order(12)
    void pathStyleAndVirtualHostSeeTheSameData() {
        // Object created via virtual-host should be visible via path-style
        given()
        .when()
            .get("/" + BUCKET + "/hello.txt")
        .then()
            .statusCode(200)
            .body(equalTo("virtual hosted content"));
    }

    @Test
    @Order(13)
    void headBucketReturns404ForMissingRegionQualifiedHost() {
        given()
            .header("Host", REGION_HOST)
        .when()
            .head("/")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(14)
    void createBucketViaRegionQualifiedVirtualHost() {
        given()
            .header("Host", REGION_HOST)
        .when()
            .put("/")
        .then()
            .statusCode(200)
            .header("Location", equalTo("/" + REGION_BUCKET));
    }

    @Test
    @Order(15)
    void headBucketViaRegionQualifiedVirtualHost() {
        given()
            .header("Host", REGION_HOST)
        .when()
            .head("/")
        .then()
            .statusCode(200)
            .header("x-amz-bucket-region", notNullValue());
    }

    @Test
    @Order(16)
    void putAndGetObjectViaRegionQualifiedVirtualHost() {
        given()
            .header("Host", REGION_HOST)
            .contentType("text/plain")
            .body("region-qualified content")
        .when()
            .put("/region.txt")
        .then()
            .statusCode(200);

        given()
            .header("Host", REGION_HOST)
        .when()
            .get("/region.txt")
        .then()
            .statusCode(200)
            .body(equalTo("region-qualified content"));
    }

    @Test
    @Order(17)
    void listBucketsViaLocalStackS3ServiceHost() {
        given()
            .header("Host", "s3.localhost.localstack.cloud")
        .when()
            .get("/")
        .then()
            .statusCode(200)
            .body(containsString("ListAllMyBucketsResult"))
            .body(containsString(BUCKET));
    }

    @Test
    @Order(18)
    void listBucketsViaFlociS3ServiceHost() {
        given()
            .header("Host", "s3.localhost.floci.io")
        .when()
            .get("/")
        .then()
            .statusCode(200)
            .body(containsString("ListAllMyBucketsResult"))
            .body(containsString(BUCKET));
    }

    @Test
    @Order(19)
    void objectKeyStartingWithCloudFrontPrefixRemainsReachable() {
        given()
            .header("Host", HOST)
            .contentType("text/plain")
            .body("ordinary s3 object")
        .when()
            .put("/_cloudfront/file.txt")
        .then()
            .statusCode(200);

        given()
            .header("Host", HOST)
        .when()
            .get("/_cloudfront/file.txt")
        .then()
            .statusCode(200)
            .body(equalTo("ordinary s3 object"));

        given().header("Host", HOST).delete("/_cloudfront/file.txt")
                .then().statusCode(204);
    }

    @Test
    @Order(20)
    void proxiedStreamingPutUsesForwardedBucketHost() {
        String content = "proxied streaming content";
        String streamingBody = "19;chunk-signature=0\r\n"
                + content
                + "\r\n0;chunk-signature=0\r\n"
                + "x-amz-checksum-sha256:iprRw1fqgvKxbi49RuMxy4yK0KXk/wE4O0UR2R7gB9A=\r\n\r\n";

        given()
        .when()
            .put("/" + PROXY_BUCKET)
        .then()
            .statusCode(200);

        given()
            .header("Host", "localhost:4566")
            .header("X-Forwarded-Host", PROXY_HOST)
            .header("Content-Encoding", "aws-chunked")
            .header("x-amz-content-sha256", "STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER")
            .header("x-amz-decoded-content-length", content.length())
            .header("x-amz-trailer", "x-amz-checksum-sha256")
            .contentType("application/octet-stream")
            .queryParam("x-id", "PutObject")
            .body(streamingBody.getBytes(StandardCharsets.ISO_8859_1))
        .when()
            .put("/" + PROXY_KEY)
        .then()
            .statusCode(200)
            .header("ETag", notNullValue());

        given()
        .when()
            .get("/" + PROXY_BUCKET + "/" + PROXY_KEY)
        .then()
            .statusCode(200)
            .body(equalTo(content));

        given()
        .when()
            .get("/")
        .then()
            .statusCode(200)
            .body(containsString("<Name>" + PROXY_BUCKET + "</Name>"))
            .body(not(containsString("<Name>" + PROXY_KEY + "</Name>")));
    }

    @Test
    @Order(21)
    void cleanupAndDeleteBucket() {
        given().header("Host", HOST).delete("/hello.txt");
        given().header("Host", HOST).delete("/path/to/nested.json");

        given()
            .header("Host", HOST)
        .when()
            .delete("/")
        .then()
            .statusCode(204);

        given().header("Host", REGION_HOST).delete("/region.txt");
        given()
            .header("Host", REGION_HOST)
        .when()
            .delete("/")
        .then()
            .statusCode(204);

        given().delete("/" + PROXY_BUCKET + "/" + PROXY_KEY);
        given()
        .when()
            .delete("/" + PROXY_BUCKET)
        .then()
            .statusCode(204);
    }
}
