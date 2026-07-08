import com.fasterxml.jackson.databind.ObjectMapper;

public class test_json {
    public static class CreateReviewRequest {
        private int score;
        private String comment;
        public int getScore() { return score; }
        public String getComment() { return comment; }
    }
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"score\":5, \"comment\":\"Great!\"}";
        CreateReviewRequest req = mapper.readValue(json, CreateReviewRequest.class);
        System.out.println("Score: " + req.getScore());
        System.out.println("Comment: " + req.getComment());
    }
}
