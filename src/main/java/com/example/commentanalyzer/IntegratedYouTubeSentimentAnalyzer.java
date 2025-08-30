package com.example.commentanalyzer;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.LiveChatMessageListResponse;
import com.google.api.services.youtube.model.LiveChatMessage;
import com.google.api.services.youtube.model.LiveChatSuperChatDetails;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoLiveStreamingDetails;
import com.google.api.services.youtube.model.VideoListResponse;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


 //統合版：YouTubeライブチャットのリアルタイム感情分析システム
 //YouTubeからコメントを取得し、リアルタイムで感情分析を行う
public class IntegratedYouTubeSentimentAnalyzer {
    
    // Azure Text Analytics API の設定
    private  final String API_KEY;
    private  final String ENDPOINT;
    private  final String API_URL;
    
    // 感情分析のバッチサイズ（一度に分析するコメント数）
    private static final int ANALYSIS_BATCH_SIZE = 5;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<CommentData> comments;
    private final YouTube youtube;
    
    // バッチ処理用の未分析コメントカウンター
    private int unanalyzedCount = 0;

    public IntegratedYouTubeSentimentAnalyzer(YouTube youtube) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.comments = new ArrayList<>();
        this.youtube = youtube;

        // 環境変数からAzure API設定を取得
        this.API_KEY = getRequiredEnvironmentVariable("AZURE_API_KEY");
        this.ENDPOINT = getRequiredEnvironmentVariable("AZURE_ENDPOINT");
        this.API_URL = this.ENDPOINT + "/text/analytics/v3.1/sentiment";

        // 設定確認
        System.out.println("Azure API設定確認:");
        System.out.println("  エンドポイント: " + this.ENDPOINT);
        System.out.println("  APIキー: " + maskApiKey(this.API_KEY));
    }

        private String getRequiredEnvironmentVariable(String name) {
            String value = System.getenv(name);
            if (value == null || value.trim().isEmpty()) {
                throw new RuntimeException(
                    "環境変数 '" + name + "' が設定されていません。\n" +
                    "設定方法:\n" +
                    "  Windows: set " + name + "=your_value_here\n" +
                    "  Mac/Linux: export " + name + "=\"your_value_here\""
                );
            }
            return value.trim();
        }
      
     //APIキーを一部マスクして表示用に変換
    
    private String maskApiKey(String apiKey) {
        if (apiKey.length() <= 8) return "****";
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);

    }

    
     //コメントデータを表すクラス
     
    public static class CommentData {
        public String text;
        public String author;
        public LocalDateTime timestamp;
        public double sentimentScore;
        public String messageType; // "text", "superchat", "other"
        
        public CommentData(String text, String author, LocalDateTime timestamp, String messageType) {
            this.text = text;
            this.author = author;
            this.timestamp = timestamp;
            this.messageType = messageType;
            this.sentimentScore = 0.0; // 未分析
        }
        
        @Override
        public String toString() {
            return String.format("CommentData{author='%s', text='%s', type='%s', score=%.2f}", 
                               author, text, messageType, sentimentScore);
        }
    }
    
    
     //YouTubeライブチャットからコメントを取得し、リアルタイムで感情分析を実行
     
    public void startLiveChatAnalysis(String videoId) {
        try {
            String liveChatId = getLiveChatId(videoId);
            if (liveChatId == null) {
                System.out.println("ライブチャットが見つかりませんでした。");
                return;
            }
            
            System.out.println("ライブチャット分析を開始します...");
            String nextPageToken = null;
            
            while (true) {
                // YouTubeからコメントを取得
                YouTube.LiveChatMessages.List request = youtube.liveChatMessages()
                    .list(liveChatId, Arrays.asList("snippet", "authorDetails"));
                
                if (nextPageToken != null) {
                    request.setPageToken(nextPageToken);
                }
                
                LiveChatMessageListResponse response = request.execute();
                List<LiveChatMessage> messages = response.getItems();
                
                // 取得したコメントを感情分析システムに追加
                for (LiveChatMessage message : messages) {
                    addCommentFromYouTube(message);
                }
                
                // バッチサイズに達したら感情分析を実行
                if (unanalyzedCount >= ANALYSIS_BATCH_SIZE) {
                    analyzePendingSentiments();
                    printRecentAnalysis();
                }
                
                nextPageToken = response.getNextPageToken();
                long interval = response.getPollingIntervalMillis();
                Thread.sleep(interval);
            }
            
        } catch (IOException e) {
            System.err.println("YouTubeライブチャット取得エラー: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("分析が中断されました。");
            // 最終的な分析とCSV出力
            // シャットダウンフックで最終処理を行う
            Thread.currentThread().interrupt(); // インタラプトフラグを復元

        } catch (Exception e) {
            System.err.println("予期しないエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    //YouTubeメッセージからコメントデータを作成し、リストに追加

    private void addCommentFromYouTube(LiveChatMessage message) {
        String author = message.getAuthorDetails().getDisplayName();
        String text = "";
        String messageType = "";
        
        switch (message.getSnippet().getType()) {
            case "textMessageEvent":
                text = message.getSnippet().getTextMessageDetails().getMessageText();
                messageType = "text";
                break;
            case "superChatEvent":
                LiveChatSuperChatDetails sc = message.getSnippet().getSuperChatDetails();
                String comment = sc.getUserComment();
                text = comment != null ? comment : "[スーパーチャット]";
                messageType = "superchat";
                break;
            default:
                text = "[" + message.getSnippet().getType() + "]";
                messageType = "other";
        }
        
        // 感情分析対象のコメントのみ追加（テキストがある場合）
        //!text.startsWith("[" ここはシステムメッセージをはじくための条件
        if (!text.isEmpty() && !text.startsWith("[")) {
            CommentData commentData = new CommentData(text, author, LocalDateTime.now(), messageType);
            comments.add(commentData);
            unanalyzedCount++;
            
            System.out.printf("[%s] %s: %s%n", messageType.toUpperCase(), author, text);
        }
    }
    
    
    //ライブチャットIDを取得
     
    private String getLiveChatId(String videoId) throws IOException {
        YouTube.Videos.List videoRequest = youtube.videos()
            .list(Arrays.asList("liveStreamingDetails"))
            .setId(Arrays.asList(videoId));
        
        VideoListResponse videoResponse = videoRequest.execute();
        List<Video> videos = videoResponse.getItems();
        
        if (videos.isEmpty()) {
            System.out.println("指定された動画が見つかりませんでした。");
            return null;
        }
        
        VideoLiveStreamingDetails details = videos.get(0).getLiveStreamingDetails();
        if (details != null) {
            return details.getActiveLiveChatId();
        }
        
        return null;
    }
    

     //未分析のコメントに対して感情分析を実行
     
    private void analyzePendingSentiments() {
        List<CommentData> unanalyzedComments = comments.stream()
            .filter(comment -> comment.sentimentScore == 0.0)
            .collect(Collectors.toList());
        
        if (unanalyzedComments.isEmpty()) {
            return;
        }
        
        System.out.println(unanalyzedComments.size() + "件のコメントを感情分析中...");
        
        try {
            String requestJson = createSentimentAnalysisRequest(unanalyzedComments);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Ocp-Apim-Subscription-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                //HttpRequest.BodyPublishers.ofString(...)は「送る郵便物の中身は、このJSON文字列」と指定している。
                .build();
                //一連の設定が終わった後、最後に.build()を呼び出し、設定した情報に基づいて最終的なHttpRequestオブジェクトを生成。
                //このオブジェクトは、次のステップで実際にサーバーに送信。
            
                //sendメソッドの呼び出しと二つの引数。
                //HttpResponse.BodyHandlers.ofString():
                //これは、サーバーから返ってきたレスポンスのボディ（本体）をどのように扱いたいかを
                //httpClientに伝えるための指示。BodyHandlersは、レスポンスボディを特定の形式で処理するためのヘルパークラス。
                //.ofString()というメソッドは、「サーバーからの応答ボディを文字列として読み取ってください」という命令。
                //これにより受け取ったデータがそのままString型の変数に格納できるようになる。

                //つまりこの1文で
                //リクエスト送信: requestオブジェクトに従って、ネットワーク経由でAzureの感情分析APIサーバーにデータを送信。
                //応答待機: サーバーからの応答が返ってくるまで処理を一時停止。
                //応答受信と処理: サーバーから応答が届くと、ofString()の指示に従ってレスポンスボディを文字列として読み込みます。
                //結果の返却: 最終的に、読み込んだボディを<String>型のHttpResponseオブジェクトに格納し、
                //そのオブジェクトを返す。この戻り値がresponse変数に代入され、次の処理で使えるようになる。
                //これらのことが行われている。
            HttpResponse<String> response = httpClient.send(request, 
                                                          HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                parseSentimentResponse(response.body(), unanalyzedComments);
                unanalyzedCount = 0; // リセット
                System.out.println("感情分析完了");
            } else {
                System.err.println("API呼び出しエラー: " + response.statusCode());
                System.err.println("レスポンス: " + response.body());
            }
            
        } catch (Exception e) {
            System.err.println("感情分析エラー: " + e.getMessage());
        }
    }
    
    
     //最新の分析結果を表示
    
    private void printRecentAnalysis() {
        //.stream()は、commentsというListの要素をStreamに変換しています。ストリームは、データを処理するための一連の操作
        //（フィルタリング、ソート、集計など）を流れるように実行するための機能で
        //元のデータ構造（この場合はList）とは異なる。
        //ストリームは元のリストの要素をそのまま変更するのではなく、
        //要素を一時的な「データの流れ」として扱い、様々な加工を連鎖的に行えるようにしている。    
        List<CommentData> recentAnalyzed = comments.stream()
        //c -> c.sentimentScore != 0.0**というラムダ式は、「cというCommentDataオブジェクトのsentimentScoreが0.0と等しくない場合
        //（つまり、すでに分析済みの場合）に、そのオブジェクトを残す」という条件を表している。
            .filter(c -> c.sentimentScore != 0.0)
            //(a, b) -> b.timestamp.compareTo(a.timestamp)というラムダ式は
            //2つのCommentDataオブジェクトaとbを比較する方法を定義、bのタイムスタンプとaのタイムスタンプを比較。
            //compareToメソッドは昇順（古い順）に並べるが、引数の順番をbとaを逆にすることで
            //結果的に新しい順（降順）にコメントを並べ替え。
            .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
            //5件選ぶ
            .limit(5)
            //Collectors.toList()は、「これまでの操作で残った要素をすべて新しいListに集めてください」という命令
            .collect(Collectors.toList());
            //つまり選ばれた5件のコメントを新しいListに集めて、このあとのrecentAnalyzed変数に代入する
        
            //\nを置くことで、見出しの前に空の行を追加。前の出力と新しい出力の間に1行の空白を作ることで
            //コンソール画面をより見やすく、読みやすくするための工夫。
        System.out.println("\n最新の感情分析結果:");
        //拡張forループ
        for (CommentData comment : recentAnalyzed) {
            String sentiment = getSentimentLabel(comment.sentimentScore);
            System.out.printf("  %s [%s] %s: %.3f%n", 
                            sentiment, comment.author, comment.text, comment.sentimentScore);
        }
        //このforループで、ポジティブ [ユーザー名] コメント内容: スコア　を見やすく出力%nは改行の意味
        
        // 全体的な傾向を表示
        double averageScore = comments.stream() //コメントデータのリストをストリームに変換。
            .filter(c -> c.sentimentScore != 0.0) //ストリームから、sentimentScoreが0.0ではない（分析済みの）コメントだけを抽出。
            //分析されていないコメントが平均計算に含まれるのを防ぐ
            .mapToDouble(c -> c.sentimentScore)//各CommentDataオブジェクトをその感情スコア（double型）だけに変換。
            //この操作により、ストリームはdouble型の数値の集まりになる。
            .average().orElse(0.0);
        
        System.out.printf("現在の平均感情スコア: %.3f (%s)%n%n", 
                        averageScore, getSentimentLabel(averageScore));
    }
        //mapメソッドの役割
        //mapの基本的な役割は、ストリームを流れる各要素に対して指定された関数を適用し
        //その関数の結果を新しい要素として持つストリームを生成すること。
        //複数のりんごが流れているベルトコンベア（ストリーム）があるとして
        //map操作は、そのベルトコンベアの途中に「りんごをジュースに変える機械」を置くようなもの。
        //その結果、ベルトコンベアは「りんごジュース」だけが流れるようになる。ストリームレベルでの変換器

    
    
     //感情スコアをラベルに変換
     
    private String getSentimentLabel(double score) {
        if (score > 0.3) return "ポジティブ";
        if (score < -0.3) return "ネガティブ";
        return "ニュートラル";
    }
    
    
    //Azure APIリクエスト用のJSONを作成
    //stringは基本変更不可能（immutable）なものなので、都度オブジェクトが作られる。
    //StringBuilderを使えば、Stringのように新しいオブジェクトを何度も作ることなく、文字列を連結できる。
    //StringとStringBuilderの動作の違い
    //String:「Hello」というオブジェクトがメモリに作られそこにworldという文字列を連結したときは
    //「Hello World」という別の新しいオブジェクトが作られ、以前のhelloオブジェクトは捨てられる。
    //StringBuilder:「Hello」というオブジェクトがメモリに作られ、そのオブジェクトに対して「World」が直接追加される。
    //StringBuilderは、内部で可変な（変更できる）文字の配列を持っており、そこに直接文字を追加していくイメージ。
    //これにより、たくさんの文字列を連結する際のパフォーマンスが大幅に向上する。
    private String createSentimentAnalysisRequest(List<CommentData> commentsToAnalyze) {
        // Azure APIの制限確認
        if (commentsToAnalyze.size() > 10) {
            System.err.println("警告: バッチサイズが10を超えています: " + commentsToAnalyze.size());
            commentsToAnalyze = commentsToAnalyze.subList(0, 10);
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"documents\": [");
        //JSON文字列の最初の部分を追加している。
        //この文脈での\は、直後にある"（ダブルクォート）が単なる文字列ではなく
        //特別な意味を持つ文字であることをコンパイラに伝えている。
        //JSON（JavaScript Object Notation）の構文では、文字列は必ずダブルクォートで囲まなければならないためである。
        //{}はJSONオブジェクト、[]はJSON配列を示す。
        //この文字列は、「documentsというキーを持つJSONオブジェクトを開始し、その値は配列です」という意味になる
        //キーと値の関係を:で区切るのがJSONのルールのためコロンがある。
        

        //感情分析をリクエストするためのJSON配列の各要素を組み立てている。
        //複数のコメントを1つのAPIリクエストでまとめて送れるようにコメントごとにJSONオブジェクトを作成し、
        //それらをカンマで区切りながらStringBuilderに追加。
        for (int i = 0; i < commentsToAnalyze.size(); i++) {
            if (i > 0) json.append(",");
            json.append(String.format(
                "{\"id\": \"%d\", \"language\": \"ja\", \"text\": \"%s\"}", //これはJSONオブジェクトのテンプレート。
                //idの値を埋め込むためのプレースホルダー（dは整数）。textの値を埋め込むためのプレースホルダー
                i, //テンプレートの%dに、ループのインデックスiが埋め込まれ、各コメントにユニークなidが割り当てられる
                commentsToAnalyze.get(i).text.replace("\"", "\\\"")
                //JSONでは、文字列は必ずダブルクォート（"）で囲まなければならない。
                //しかし、コメントのテキスト自体にダブルクォートが含まれている場合、問題が起きるのでエスケープ処理
                //バックスラッシュ直後の記号が「ただの文字だよ」という意味にするために
                //変換された後に\"とならねばならない。なので"を\"に変換している
            ));
        }
        
        json.append("]}");//JSONの構文を正しく閉じるための処理。]はJSON配列の終わり、}はJSONオブジェクトの終わりを示す
        return json.toString();//完成したJSON文字列をメソッドの戻り値として返している
    }
                                //json.toString()は、StringBuilderオブジェクト（json）を
                                //不変な（immutable）Stringオブジェクトに変換。
                                //StringBuilderは内部で効率的に文字列を操作するためのものなので
                                //文字列の構築が終わったら、最終的なStringに再変換して利用。

    
    //Azure APIのレスポンスを解析
    //JsonNodeは、JSONデータをツリー構造で扱うためのクラスであり
    //そのクラスのインスタンス（オブジェクト）を指す変数をrootという名前で宣言している。
    //rootは、提供されたコードの中であなたが任意で名付けた変数です。JsonNodeは、JSONデータをツリー構造で扱うためのクラスであり、そのクラスのインスタンス（オブジェクト）を指す変数をrootという名前で宣言しています。
    //JSONのデータ構造とJsonNode
    //JSONデータは、テキストとして記述されるが、プログラムでこのテキストを直接操作するのは非常に手間がかかる。
    //そこで、objectMapper.readTree()メソッドでこのテキスト群をメモリ上で操作しやすい階層的なデータ構造（ツリー）に変換
    //その根をrootとしている
    //readTree()メソッドは、引数として受け取ったJSONテキストを解析し、プログラムで扱いやすいツリー構造に変換するように設計されている。
    //そして、そのツリー構造の一番上のノード（ルートノード）を返すのが、このメソッドの役割。
    //これは、JSONライブラリが提供する基本的な機能であり、開発者が複雑な文字列操作をすることなく、
    //階層的なJSONデータに簡単にアクセスできるようにするためのもの。
    private void parseSentimentResponse(String responseBody, List<CommentData> analyzedComments) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode documents = root.get("documents");//JSONツリーの中から特定のノード（"documents"というキーを持つノード）を取り出す処理。
            
            for (int i = 0; i < documents.size(); i++) {
                JsonNode document = documents.get(i);
                JsonNode confidenceScores = document.get("confidenceScores");
                //confidenceScoresはAzure APIで定義されてるフィールド
                
                double positive = confidenceScores.get("positive").asDouble();
                double neutral = confidenceScores.get("neutral").asDouble();
                double negative = confidenceScores.get("negative").asDouble();
                //confidenceScoresノードからポジティブ、ニュートラル、ネガティブの
                //3つのスコアをそれぞれ取り出し、double型に変換。
                
                double sentimentScore = (positive * 1.0) + (neutral * 0.0) + (negative * -1.0);
                analyzedComments.get(i).sentimentScore = sentimentScore;
                //CommentDataオブジェクトのsentimentScoreというフィールドに、直前の行で計算された
                //sentimentScoreというローカル変数の値を代入
            }
            
        } catch (Exception e) {
            System.err.println("レスポンス解析エラー: " + e.getMessage());
        }
    }
    
    
    //処理プログラムが終了する前に未処理のデータをすべて完了させ、
    //最終的なレポートを出力するために呼び出される関数
     
    public void finalizePendingAnalysis() {
        if (unanalyzedCount > 0) {
            System.out.println("残りの未分析コメントを処理中...");
            
            // 未分析コメントを小分けして処理
            while (unanalyzedCount > 0) {
                analyzePendingSentiments();
                try {
                    Thread.sleep(1000); // API制限を避けるための待機
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        
        printFinalSummary();
        exportToCSV("youtube_live_sentiment_" + 
                   LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv");
    }
    //.ofPattern()は、DateTimeFormatterクラスの静的メソッド。
    //これは日時の書式を定義するためのテンプレートを作成するために使う。
    //.ofPattern()メソッドは、引数として渡されたパターン文字列を解析し、
    //そのパターンに従って日時を文字列に変換するDateTimeFormatterオブジェクトを返す。
    //簡単に言うと、これは日時を好きな形式で表示するための「型紙」を作る役割を担う。
    
    
    //最終サマリーを表示
    
    private void printFinalSummary() {
        if (comments.isEmpty()) return;
        
        long analyzedCount = comments.stream()
            .mapToLong(c -> c.sentimentScore != 0.0 ? 1 : 0)
            .sum();
            // リストの各コメントをストリームに変換し感情スコアが 0.0 でない（分析済み）コメントを 1
            //それ以外を 0 に変換。最後にこれらの値をすべて合計することで、分析済みのコメントの総数を求めている。
        
        double averageScore = comments.stream()
            .filter(c -> c.sentimentScore != 0.0)
            .mapToDouble(c -> c.sentimentScore)
            .average().orElse(0.0);
            //分析済みコメントのみを抽出し、その感情スコアの平均値を計算します。もしコメントが一つもなければ 0.0 を返します。
        
        long positiveCount = comments.stream()
            .mapToLong(c -> c.sentimentScore > 0.3 ? 1 : 0)
            .sum();     
        long negativeCount = comments.stream()
            .mapToLong(c -> c.sentimentScore < -0.3 ? 1 : 0)
            .sum();
        long neutralCount = analyzedCount - positiveCount - negativeCount;
        //感情スコアが 0.3 より大きいコメントをポジティブ、-0.3 より小さいコメントをネガティブと見なし
        //それぞれの数をカウント。ニュートラルなコメントは分析済みコメント数からポジティブとネガティブの
        //合計を引くことで求めている。
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("YouTube ライブチャット感情分析 最終結果");
        System.out.println("=".repeat(50));
        System.out.println("総取得コメント数: " + comments.size());
        System.out.println("分析済みコメント数: " + analyzedCount);
        System.out.printf("全体平均スコア: %.3f (%s)%n", averageScore, getSentimentLabel(averageScore));
        System.out.println("ポジティブ: " + positiveCount + "件");
        System.out.println("ニュートラル: " + neutralCount + "件");
        System.out.println("ネガティブ: " + negativeCount + "件");
        System.out.println("=".repeat(50));
    }
    
    
    //1分ごとの平均スコアを集計
    
    // public Map<LocalDateTime, Double> calculateMinutelyAverages() {
    //     Map<LocalDateTime, List<Double>> minutelyScores = new TreeMap<>();
    //     //TreeMapは、内部的に赤黒木（Red-Black Tree）というデータ構造を使用。
    //     //これにより、キーが常に自然な順序（例: 文字列ならアルファベット順、数値なら昇順、日時なら古い順）に
    //     //自動でソートされて格納される。ハッシュマップと違って順序があるかわりにソートでオーバヘッドが生じる
        

    //     //各コメントを1分単位でグループ化し、その感情スコアを分ごとのリストにまとめる処理を行う。
    //     //後で1分ごとの平均スコアを計算するための前処理
    //     for (CommentData comment : comments) {
    //         if (comment.sentimentScore == 0.0) continue; // 未分析をスキップ
            
    //         LocalDateTime minute = comment.timestamp
    //             .withSecond(0)
    //             .withNano(0);
    //             //秒とナノ秒を0に設定することで「分」単位に切り捨て丸めている
    //             //これにより、同じ分に投稿されたすべてのコメントが
    //             //同じLocalDateTimeオブジェクトを持つことになり、後のグループ化の基準となる
            

    //             //computeIfAbsent()は、Java 8以降で標準で用意されているMapインターフェースのメソッド
    //             //これは「キーの存在チェックと、それに続く処理」を1つのメソッドにまとめたもの
    //             //「分」というキーと「その分の感情スコアのリスト」という値がちゃんとあるかのチェック
    //             //もし存在しない場合は、新しいArrayListを作成して
    //             //そのキーに対応する値としてマップに追加し、その新しいリストを返します。
    //         minutelyScores.computeIfAbsent(minute, k -> new ArrayList<>())
    //                      .add(comment.sentimentScore);  //computeIfAbsentが返したリストに対して現在のコメントのsentimentScoreを追加。
    //     }
        
    //     //最終的な計算結果を格納するための新しいMapを作成、キーは1分ごとの日時、値はその分の平均スコア
    //     //TreeMapを使用しているため、計算結果が自動的に時間順にならぶ
    //     Map<LocalDateTime, Double> averages = new TreeMap<>(); 
    //     //entry: minutelyScoresマップのキーと値のペアを指す変数。
    //     //ここの行はminutelyScoresマップに格納されたすべてのキーと値をentrySet()で返してentryにひとつずついれているループ
    //     for (Map.Entry<LocalDateTime, List<Double>> entry : minutelyScores.entrySet()) {
    //         List<Double> scores = entry.getValue();
    //         //entryという変数に格納されているキーと値のペアから、値の部分（List<Double>）を取り出し、scoresという新しい変数に代入
            
            
    //         //scores.stream()スコアリストをストリームに変換、.mapToDouble(Double::doubleValue)で
    //         //ストリーム内の各要素（Double型のオブジェクト）を、プリミティブ型のdouble値に変換
    //         //Double::doubleValueは、d -> d.doubleValue()と同じ意味を持つメソッド参照
    //         double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    //         //感情スコアの平均値を計算し、その結果をaverageという変数に代入しています。
    //         averages.put(entry.getKey(), average);
    //         //averagesというマップにentry.getKey()で得られた「分」をキーとして
    //         //averageで計算された「平均スコア」を値として格納しろという指示
    //     }
        
    //     return averages;
    // }
    

//10秒ごとの平均スコアを集計に変更
    
public Map<LocalDateTime, Double> calculateTenSecondlyAverages() {
    Map<LocalDateTime, List<Double>> tenSecondlyScores = new TreeMap<>();
    //TreeMapは、内部的に赤黒木（Red-Black Tree）というデータ構造を使用。
    //これにより、キーが常に自然な順序（例: 文字列ならアルファベット順、数値なら昇順、日時なら古い順）に
    //自動でソートされて格納される。ハッシュマップと違って順序があるかわりにソートでオーバヘッドが生じる
    

    //各コメントを10秒単位でグループ化し、その感情スコアを10秒ごとのリストにまとめる処理を行う。
    //後で10秒ごとの平均スコアを計算するための前処理
    for (CommentData comment : comments) {
        if (comment.sentimentScore == 0.0) continue; // 未分析をスキップ
        
        // 10秒単位に切り捨て（秒を10で割って切り捨て、再び10倍することで10秒単位にする）
        int roundedSeconds = (comment.timestamp.getSecond() / 10) * 10;
        LocalDateTime tenSecondInterval = comment.timestamp
            .withSecond(roundedSeconds)
            .withNano(0);
            //秒を10秒単位に切り捨て、ナノ秒を0に設定することで「10秒」単位に丸めている
            //これにより、同じ10秒間に投稿されたすべてのコメントが
            //同じLocalDateTimeオブジェクトを持つことになり、後のグループ化の基準となる
        

            //computeIfAbsent()は、Java 8以降で標準で用意されているMapインターフェースのメソッド
            //これは「キーの存在チェックと、それに続く処理」を1つのメソッドにまとめたもの
            //「10秒間隔」というキーと「その10秒間の感情スコアのリスト」という値がちゃんとあるかのチェック
            //もし存在しない場合は、新しいArrayListを作成して
            //そのキーに対応する値としてマップに追加し、その新しいリストを返します。
        tenSecondlyScores.computeIfAbsent(tenSecondInterval, k -> new ArrayList<>())
                     .add(comment.sentimentScore);  //computeIfAbsentが返したリストに対して現在のコメントのsentimentScoreを追加。
    }
    
    //最終的な計算結果を格納するための新しいMapを作成、キーは10秒ごとの日時、値はその10秒間の平均スコア
    //TreeMapを使用しているため、計算結果が自動的に時間順にならぶ
    Map<LocalDateTime, Double> averages = new TreeMap<>(); 
    //entry: tenSecondlyScoresマップのキーと値のペアを指す変数。
    //ここの行はtenSecondlyScoresマップに格納されたすべてのキーと値をentrySet()で返してentryにひとつずついれているループ
    for (Map.Entry<LocalDateTime, List<Double>> entry : tenSecondlyScores.entrySet()) {
        List<Double> scores = entry.getValue();
        //entryという変数に格納されているキーと値のペアから、値の部分（List<Double>）を取り出し、scoresという新しい変数に代入
        
        
        //scores.stream()スコアリストをストリームに変換、.mapToDouble(Double::doubleValue)で
        //ストリーム内の各要素（Double型のオブジェクト）を、プリミティブ型のdouble値に変換
        //Double::doubleValueは、d -> d.doubleValue()と同じ意味を持つメソッド参照
        double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        //感情スコアの平均値を計算し、その結果をaverageという変数に代入しています。
        averages.put(entry.getKey(), average);
        //averagesというマップにentry.getKey()で得られた「10秒間隔」をキーとして
        //averageで計算された「平均スコア」を値として格納しろという指示
    }
    
    return averages;
}


    
    //CSV出力
    
    // public void exportToCSV(String filename) {
    //     Map<LocalDateTime, Double> averages = calculateMinutelyAverages();


    //CSV出力（10秒間隔に変更）    
    public void exportToCSV(String filename) {
        Map<LocalDateTime, Double> averages = calculateTenSecondlyAverages();

        //new FileWriter(filename)
        //引数で渡されたfilename（ファイル名）を基に、ファイルとの間に「書き込みのための接続」を確立する役割
        //new PrintWriter(...)
        //PrintWriterは、FileWriterが持つ基本的な書き込み機能の上に
        //より高度で便利な機能（例: println()やprintf()など）を追加する役割を担う
        //println()は、データを書き込んだ後に自動で改行してくれる機能
        //FileWriter:は土管のイメージ、PrintWriter: これはじょうろやフィルターのイメージ
        //土管を通して運ばれてきたデータ(水)を、書式を整えて（細かく調整して）目的地にきれいに流し込む役割
        //PrintWriterがFileWriterを「ラップ」することで、より使いやすく、安全にファイルにデータを書き込めるようになっている。
        //この多段階の構造は、Javaのライブラリでよく見られるデザインパターン...らしい
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("時間,平均感情スコア,コメント数,ポジティブ数,ネガティブ数,ニュートラル数");
            
            //一個前の関数でminutelyScoreからのループで作ったavaragesマップを使って更にループ
            //averagesマップのすべてのペア（キーと値の組み合わせ）のセットを返す。
            //このペアは、LocalDateTime（分）とDouble（平均スコア）の組み合わせ。
            //変更後このペアは、LocalDateTime（10秒間隔）とDouble（平均スコア）の組み合わせ。
            for (Map.Entry<LocalDateTime, Double> entry : averages.entrySet()) {
                LocalDateTime time = entry.getKey();
                double avgScore = entry.getValue();
                
                //すべてのコメントが格納されたcommentsリストをストリームに変換
                // List<CommentData> minuteComments = comments.stream()
                //コメントのタイムスタンプの秒とナノ秒を0に設定し、分単位に丸め
                //そのタイムスタンプが現在のループで処理しているtime（averagesマップのキー）と等しいかどうかを比較。
                    // .filter(c -> c.timestamp.withSecond(0).withNano(0).equals(time))
                    // .filter(c -> c.sentimentScore != 0.0)
                    //感情スコアが0.0ではない（＝分析済み）コメントだけを絞り込む。未分析のコメントが混入するのを防ぐ

                    //コメントのタイムスタンプを1分単位から10秒単位に丸める処理に変更
                List<CommentData> intervalComments = comments.stream()
                .filter(c -> {
                    int roundedSeconds = (c.timestamp.getSecond() / 10) * 10;
                    LocalDateTime roundedTime = c.timestamp.withSecond(roundedSeconds).withNano(0);
                    return roundedTime.equals(time);
                })
                //そのタイムスタンプが現在のループで処理しているtime（averagesマップのキー）と等しいかどうかを比較。
                    .filter(c -> c.sentimentScore != 0.0)
                    //感情スコアが0.0ではない（＝分析済み）コメントだけを絞り込む。未分析のコメントが混入するのを防ぐ
                    .collect(Collectors.toList());
                    //それを新しく作るリストにぶちこみ、これをminuteCommentsに代入
                
                //minuteCommentsリストのサイズ（要素数）を取得、その分(時間)に投稿された分析済みのコメントの総数をcommentCountという変数に代入
                // long commentCount = minuteComments.size();
                // //リストをストリームに変換し、感情スコアが0.3より大きいコメントをポジティブと見なしてカウント
                // long positiveCount = minuteComments.stream().mapToLong(c -> c.sentimentScore > 0.3 ? 1 : 0).sum();
                // //0.3より小さいコメントをネガティブ
                // long negativeCount = minuteComments.stream().mapToLong(c -> c.sentimentScore < -0.3 ? 1 : 0).sum();

                //その10秒間に投稿された分析済みのコメントの総数をcommentCountという変数に代入
                long commentCount = intervalComments.size();
                long positiveCount = intervalComments.stream().mapToLong(c -> c.sentimentScore > 0.3 ? 1 : 0).sum();
                long negativeCount = intervalComments.stream().mapToLong(c -> c.sentimentScore < -0.3 ? 1 : 0).sum();
                long neutralCount = commentCount - positiveCount - negativeCount;//全体から引いてニュートラルの数をだす
                
            //     writer.printf("%s,%.3f,%d,%d,%d,%d%n", 
            //                 time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            //                 avgScore, commentCount, positiveCount, negativeCount, neutralCount);
            // }       //書式設定での出力。%sは文字列、%.3fは小数3桁以下、%dは整数
                
                writer.printf("%s,%.3f,%d,%d,%d,%d%n", 
                time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                avgScore, commentCount, positiveCount, negativeCount, neutralCount);
}               //書式設定での出力。%sは文字列、%.3fは小数3桁以下、%dは整数
                //時刻フォーマットも秒まで表示するように変更

            System.out.println("CSVファイルを出力しました: " + filename);
            
        } catch (IOException e) {
            System.err.println("CSV出力エラー: " + e.getMessage());
        } finally {
            finalizePendingAnalysis();
        }
    }
}