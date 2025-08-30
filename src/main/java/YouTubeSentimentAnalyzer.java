import java.io.*;   //ファイル操作のクラス
import java.net.URI; //HTTP通信のクラスが4つ
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime; //日付・時刻の処理のクラス2つ
import java.time.format.DateTimeFormatter;
import java.util.*;  //リストやマップなどのデータ構造とストリームAPI（データの流れを扱う機能）のクラス
import java.util.stream.Collectors;

//Jacksonという外部ライブラリのクラスのインポート
//JSON形式のデータをJavaオブジェクトに変換したり、その逆を行ったりする。
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;



//   YouTube配信のリアルタイムコメント感情分析システム
//   Azure Text Analytics APIを使用してコメントの感情を分析し、
//  1分ごとの平均スコアをCSV形式で出力
 
public class YouTubeSentimentAnalyzer {
    
    // Azure Text Analytics API の設定
    private static final String API_KEY = "";
    private static final String ENDPOINT = ""; 
    private static final String API_URL = ENDPOINT + "";
    
    // HTTP クライアント（Java 11以降の標準機能）
    //外部のAPIと通信するためのHTTPクライアントを保持する変数
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper; // JSONを扱うためのライブラリ
    private final List<CommentData> comments; // コメントデータを保存するリスト
    //取得した全てのコメントデータを保存するリスト。
    //CommentDataという独自のクラスのオブジェクトをこのリストに追加。
    

    // コンストラクタ
    public YouTubeSentimentAnalyzer() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.comments = new ArrayList<>();
    }
    
    /**
     * コメントデータを表すクラス
     * 各コメントの情報（テキスト、投稿時間、感情スコア）を保持
     */
    public static class CommentData {
        public String text;           // コメント内容
        public LocalDateTime timestamp; // 投稿時間
        public double sentimentScore; // 感情スコア（-1.0 ～ +1.0）
        
        public CommentData(String text, LocalDateTime timestamp) {
            this.text = text;
            this.timestamp = timestamp;
            this.sentimentScore = 0.0; // 初期値
        }
        
        //toString()メソッドのオーバーライドは、Javaのオブジェクトを人間が読みやすい文字列に変換するためのもの。
        //これにより、デバッグやログ出力の際に、オブジェクトの中身を簡単に確認できるようになる。
        //toString()メソッドの役割
        //Javaのすべてのクラスは、最上位のクラスであるObjectクラスを継承していて
        //ObjectクラスにはtoString()というメソッドが定義されてる。
        //もしこのメソッドをオーバーライドせずにCommentDataオブジェクトをそのまま表示しようとすると
        //CommentData@1e389b2cといった意味不明な文字列が出力される。
        //これはオブジェクトのクラス名とメモリ上のアドレス（ハッシュコード）を表す文字列で人間にはほとんど意味がない。
        //オーバーライドする理由
        //デバッグ: オブジェクトの状態をすばやく確認するため。
        //ログ出力: エラーやイベントが発生した際に、オブジェクトの現在の状態をログファイルに記録するため。
        //これにより後から問題の原因を追跡しやすくなる。
        //コンソールへの表示: System.out.println()のような標準出力メソッドにオブジェクトを渡すと
        //Javaは内部的にtoString()を呼び出して、オブジェクトの情報を表示可能な文字列に変換します。
        //今回のコードでは、CommentDataクラスのtoString()をオーバーライド。
        //これによって、CommentDataオブジェクトを文字列に変換する際に開発者が意図したフォーマットで情報を表示。
 
        @Override
        public String toString() {
            return String.format("CommentData{text='%s', timestamp=%s, score=%.2f}", 
                               text, timestamp, sentimentScore);
        }
    }
    
    
    //コメントを追加するメソッド、YouTubeから取得したコメントをシステムに追加
     
    public void addComment(String commentText) {
        CommentData comment = new CommentData(commentText, LocalDateTime.now());
        comments.add(comment);
        System.out.println("コメントを追加しました: " + commentText);
    }
    
   
    //Azure Text Analytics APIを呼び出して感情分析を実行
    //複数のコメントを一度に分析できるよう、バッチ処理で実装
    
    public void analyzeSentiments() {
        if (comments.isEmpty()) {
            System.out.println("分析するコメントがありません。");
            return;
        }
        
        
            // まだ分析していないコメントを抽出
            //filterやcollectは、Java 8から導入された「Stream API」という機能の一部。
            //これらは、Listなどのコレクションの要素を処理するための便利なメソッドで個別に定義されたものではなく、
            //一連の処理の流れ（ストリーム）の中で連鎖的に呼び出すことが可能。

            //comments.stream()→まず、commentsというList（リスト）の要素を
            //ストリーム（データの流れ）に変換。ストリームは、水を流すパイプのようなもの。
            //この時点ではパイプを用意しただけでまだ何も処理されていない。

            //.filter(comment -> comment.sentimentScore == 0.0)
            //ここでは、このストリームにfilterという「ろ過装置」を設置している。
            //comment -> comment.sentimentScore == 0.0は、「ラムダ式」と呼ばれる、短い関数のようなもの。
            //commentは、ストリームを流れてくる各要素（CommentDataオブジェクト）を一時的に指す変数
            //->は「…を…に変換する」という意味。
            //comment.sentimentScore == 0.0が「ろ過の条件」
            //この行全体で、「sentimentScoreが0.0であるコメントだけを、次の処理に流す」という処理を指示しています。
            //.collect(Collectors.toList())
            //最後に、collectは「収集装置」ストリームの要素を最終的な形（この場合はList）に集めて変換します。
            //Collectors.toList()は、ストリームの要素を新しいListに集めるための指示です。
            //その結果のリストがunanalyzedCommentsという変数に代入されます。
            //Stream APIを使うと、複数の処理を.（ドット）でつなげて記述できるため
            //コードを簡潔かつ読みやすくすることができる。
        try {
            List<CommentData> unanalyzedComments = comments.stream()
                .filter(comment -> comment.sentimentScore == 0.0)
                .collect(Collectors.toList());
            
            if (unanalyzedComments.isEmpty()) {
                System.out.println("新しく分析するコメントがありません。");
                return;
            }
            
            System.out.println(unanalyzedComments.size() + "件のコメントを分析中...");
            
            // APIリクエスト用のJSONを作成
            String requestJson = createSentimentAnalysisRequest(unanalyzedComments);
            
            // HTTPリクエストを作成
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Ocp-Apim-Subscription-Key", API_KEY) // Azure APIキー
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
            
            // APIを呼び出し
            HttpResponse<String> response = httpClient.send(request, 
                                                          HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // レスポンスを解析して感情スコアを取得
                parseSentimentResponse(response.body(), unanalyzedComments);
                System.out.println("感情分析が完了しました。");
            } else {
                System.err.println("API呼び出しエラー: " + response.statusCode());
                System.err.println("レスポンス: " + response.body());
            }
            
        } catch (Exception e) {
            System.err.println("感情分析でエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Azure APIリクエスト用のJSONを作成
     */
    private String createSentimentAnalysisRequest(List<CommentData> commentsToAnalyze) {
        StringBuilder json = new StringBuilder();
        json.append("{\"documents\": [");
        
        for (int i = 0; i < commentsToAnalyze.size(); i++) {
            if (i > 0) json.append(",");
            json.append(String.format(
                "{\"id\": \"%d\", \"language\": \"ja\", \"text\": \"%s\"}", 
                i, 
                commentsToAnalyze.get(i).text.replace("\"", "\\\"") // ダブルクォートをエスケープ
            ));
        }
        
        json.append("]}");
        return json.toString();
    }
    
    /**
     * Azure APIのレスポンスを解析して感情スコアを計算
     */
    private void parseSentimentResponse(String responseBody, List<CommentData> analyzedComments) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode documents = root.get("documents");
            
            for (int i = 0; i < documents.size(); i++) {
                JsonNode document = documents.get(i);
                JsonNode confidenceScores = document.get("confidenceScores");
                
                // Azure APIは positive, neutral, negative の信頼度スコア（0-1）を返す
                double positive = confidenceScores.get("positive").asDouble();
                double neutral = confidenceScores.get("neutral").asDouble();
                double negative = confidenceScores.get("negative").asDouble();
                
                // スコア化：Positive=+1, Neutral=0, Negative=-1の重み付け平均を計算
                double sentimentScore = (positive * 1.0) + (neutral * 0.0) + (negative * -1.0);
                
                analyzedComments.get(i).sentimentScore = sentimentScore;
                
                System.out.printf("コメント%d: スコア=%.3f (P:%.2f, N:%.2f, Neg:%.2f)%n", 
                                i + 1, sentimentScore, positive, neutral, negative);
            }
            
        } catch (Exception e) {
            System.err.println("レスポンス解析エラー: " + e.getMessage());
        }
    }
    
    /**
     * 1分ごとの平均スコアを集計
     */
    public Map<LocalDateTime, Double> calculateMinutelyAverages() {
        Map<LocalDateTime, List<Double>> minutelyScores = new TreeMap<>();
        
        // 1分単位でグループ化（秒以下を切り捨て）
        for (CommentData comment : comments) {
            LocalDateTime minute = comment.timestamp
                .withSecond(0)
                .withNano(0);
            
            minutelyScores.computeIfAbsent(minute, k -> new ArrayList<>())
                         .add(comment.sentimentScore);
        }
        
        // 各分の平均スコアを計算
        Map<LocalDateTime, Double> averages = new TreeMap<>();
        for (Map.Entry<LocalDateTime, List<Double>> entry : minutelyScores.entrySet()) {
            List<Double> scores = entry.getValue();
            double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            averages.put(entry.getKey(), average);
        }
        
        return averages;
    }
    
    /**
     * CSV形式で結果を出力（Googleスプレッドシート対応）
     */
    public void exportToCSV(String filename) {
        Map<LocalDateTime, Double> averages = calculateMinutelyAverages();
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // CSVヘッダー
            writer.println("時間,平均感情スコア,コメント数");
            
            // データ行
            for (Map.Entry<LocalDateTime, Double> entry : averages.entrySet()) {
                LocalDateTime time = entry.getKey();
                double avgScore = entry.getValue();
                
                // その分のコメント数をカウント
                long commentCount = comments.stream()
                    .filter(c -> c.timestamp.withSecond(0).withNano(0).equals(time))
                    .count();
                
                writer.printf("%s,%.3f,%d%n", 
                            time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                            avgScore, 
                            commentCount);
            }
            
            System.out.println("CSVファイルを出力しました: " + filename);
            
        } catch (IOException e) {
            System.err.println("CSV出力エラー: " + e.getMessage());
        }
    }
    
    /**
     * 分析結果のサマリーを表示
     */
    public void printSummary() {
        if (comments.isEmpty()) {
            System.out.println("分析するデータがありません。");
            return;
        }
        
        double totalScore = comments.stream()
            .mapToDouble(c -> c.sentimentScore)
            .sum();
        double averageScore = totalScore / comments.size();
        
        long positiveCount = comments.stream()
            .mapToLong(c -> c.sentimentScore > 0.1 ? 1 : 0)
            .sum();
        long negativeCount = comments.stream()
            .mapToLong(c -> c.sentimentScore < -0.1 ? 1 : 0)
            .sum();
        long neutralCount = comments.size() - positiveCount - negativeCount;
        
        System.out.println("\n=== 感情分析結果サマリー ===");
        System.out.println("総コメント数: " + comments.size());
        System.out.printf("全体平均スコア: %.3f%n", averageScore);
        System.out.println("ポジティブ: " + positiveCount + "件");
        System.out.println("ニュートラル: " + neutralCount + "件");
        System.out.println("ネガティブ: " + negativeCount + "件");
    }
    
    /**
     * メイン実行メソッド：テスト用のサンプル
     */
    public static void main(String[] args) {
        YouTubeSentimentAnalyzer analyzer = new YouTubeSentimentAnalyzer();
        
        // テスト用のサンプルコメント
        analyzer.addComment("この配信すごく面白い！");
        analyzer.addComment("音質が悪いですね...");
        analyzer.addComment("普通のゲーム配信ですね");
        analyzer.addComment("最高の配信ありがとう！");
        analyzer.addComment("つまらない");
        
        // 感情分析実行
        analyzer.analyzeSentiments();
        
        // 結果表示
        analyzer.printSummary();
        
        // CSV出力
        analyzer.exportToCSV("sentiment_analysis_result.csv");
    }
}
