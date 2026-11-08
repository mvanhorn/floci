package io.floci.aws.services.eks;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EksClusterListIntegrationTest {
    private static final String ACCOUNT = "864213579024";

    @Test
    void listClustersPagesWithMaxResultsAndNextToken() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String first = "list-a-" + suffix;
        String second = "list-b-" + suffix;
        createCluster(first);
        createCluster(second);
        try {
            List<String> seen = new ArrayList<>();
            String token = null;
            for (int page = 0; page < 100; page++) {
                RequestSpecification request = given().header("Authorization", auth()).queryParam("maxResults", 1);
                if (token != null) {
                    request.queryParam("nextToken", token);
                }
                JsonPath body = request.get("/clusters").then().statusCode(200)
                        .body("clusters", hasSize(1))
                        .extract().jsonPath();
                seen.addAll(body.getList("clusters", String.class));
                Map<String, Object> raw = body.getMap("$");
                if (!raw.containsKey("nextToken")) {
                    break;
                }
                token = body.getString("nextToken");
                assertNotNull(token);
            }
            assertTrue(seen.containsAll(List.of(first, second)), seen.toString());

            given().header("Authorization", auth()).get("/clusters").then().statusCode(200)
                    .body("clusters", hasItems(first, second))
                    .body("$", not(hasKey("nextToken")));
        } finally {
            deleteCluster(first);
            deleteCluster(second);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "2147483648", "0", "-1", "101"})
    void invalidMaxResultsReturnsAwsJsonError(String maxResults) {
        given().header("Authorization", auth()).queryParam("maxResults", maxResults)
                .get("/clusters").then().statusCode(400)
                .contentType(containsString("application/json"))
                .body("__type", equalTo("InvalidParameterException"))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    void invalidNextTokenReturnsAwsJsonError() {
        given().header("Authorization", auth()).queryParam("nextToken", "%%%not-a-token")
                .get("/clusters").then().statusCode(400)
                .body("__type", equalTo("InvalidParameterException"));
    }

    private static void createCluster(String name) {
        given().header("Authorization", auth()).contentType("application/json")
                .body(Map.of("name", name, "roleArn", "arn:aws:iam::" + ACCOUNT + ":role/cluster"))
                .post("/clusters").then().statusCode(200);
    }

    private static void deleteCluster(String name) {
        given().header("Authorization", auth()).delete("/clusters/" + name).then().statusCode(200);
    }

    private static String auth() {
        return "AWS4-HMAC-SHA256 Credential=" + ACCOUNT
                + "/20260916/us-east-1/eks/aws4_request, SignedHeaders=host, Signature=abc";
    }
}
