import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

public class QuizLeaderboard {

    static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    static final String REG_NO = "RA2311042020015"; 
    static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
       
        Map<String, Integer> deduped = new LinkedHashMap<>();

        System.out.println("Starting 10 polls...");

        for (int poll = 0; poll <= 9; poll++) {
            System.out.println("Polling index: " + poll);
            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            System.out.println("Response: " + body);


            List<String[]> events = parseEvents(body);
            for (String[] e : events) {
                String roundId = e[0];
                String participant = e[1];
                int score = Integer.parseInt(e[2]);
                String key = roundId + "|" + participant;

             
                deduped.putIfAbsent(key, score);
            }

            if (poll < 9) {
                System.out.println("Waiting 5 seconds...");
                Thread.sleep(5000);
            }
        }

 
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : deduped.entrySet()) {
            String participant = entry.getKey().split("\\|")[1];
            scores.merge(participant, entry.getValue(), Integer::sum);
        }

   
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

      
        StringBuilder leaderboard = new StringBuilder("[");
        int totalScore = 0;
        for (int i = 0; i < sorted.size(); i++) {
            String p = sorted.get(i).getKey();
            int s = sorted.get(i).getValue();
            totalScore += s;
            leaderboard.append("{\"participant\":\"").append(p)
                       .append("\",\"totalScore\":").append(s).append("}");
            if (i < sorted.size() - 1) leaderboard.append(",");
        }
        leaderboard.append("]");

        System.out.println("\nLeaderboard: " + leaderboard);
        System.out.println("Total score: " + totalScore);

  
        String submitBody = "{\"regNo\":\"" + REG_NO + "\",\"leaderboard\":" + leaderboard + "}";
        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(submitBody))
                .build();

        HttpResponse<String> submitResponse = client.send(submitRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("\nSubmit Response: " + submitResponse.body());
    }

  
    static List<String[]> parseEvents(String json) {
        List<String[]> result = new ArrayList<>();
        int idx = json.indexOf("\"events\"");
        if (idx == -1) return result;

        int start = json.indexOf('[', idx);
        int end = json.lastIndexOf(']');
        if (start == -1 || end == -1) return result;

        String eventsArr = json.substring(start + 1, end);
      
        String[] objects = eventsArr.split("\\}");

        for (String obj : objects) {
            String roundId = extractValue(obj, "roundId");
            String participant = extractValue(obj, "participant");
            String score = extractValue(obj, "score");
            if (roundId != null && participant != null && score != null) {
                result.add(new String[]{roundId, participant, score});
            }
        }
        return result;
    }

    static String extractValue(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx == -1) return null;
        int colon = obj.indexOf(':', idx);
        if (colon == -1) return null;
        String rest = obj.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            int end = rest.indexOf('"', 1);
            return rest.substring(1, end);
        } else {
            // numeric
            StringBuilder num = new StringBuilder();
            for (char c : rest.toCharArray()) {
                if (Character.isDigit(c) || c == '-') num.append(c);
                else if (num.length() > 0) break;
            }
            return num.length() > 0 ? num.toString() : null;
        }
    }
}
