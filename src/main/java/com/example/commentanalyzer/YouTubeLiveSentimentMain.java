package com.example.commentanalyzer;

import com.google.api.services.youtube.YouTube;


    // YouTubeライブチャット感情分析システムのメインクラス
    // 使用方法：
    // 1. YouTube APIの認証を行う
    // 2. 分析したい動画IDを指定
    // 3. リアルタイム感情分析を開始
 
public class YouTubeLiveSentimentMain {
    
    public static void main(String[] args) {
        try {
            // YouTube APIクライアントの初期化
            // YouTubeAuth.getService() は既存の認証メソッドを使用
            YouTube youtube = YouTubeAuth.getService();
            
            // 統合システムのインスタンスを作成
            IntegratedYouTubeSentimentAnalyzer analyzer = 
                new IntegratedYouTubeSentimentAnalyzer(youtube);
            
            // 分析対象の動画ID（実際の配信URLから取得）
            // 例：https://www.youtube.com/watch?v=VIDEO_ID の VIDEO_ID 部分
            String videoId = getVideoIdFromEnvironment(); // コマンドプロンプトで入力
            
            System.out.println("YouTubeライブチャット感情分析システム開始");
            System.out.println("対象動画ID: " + videoId);
            System.out.println("停止するには Ctrl+C を押してください");
            System.out.println("=" + "=".repeat(60));
            
            // シャットダウンフック（Ctrl+C対応）を追加
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n\n停止処理を実行中...");
                analyzer.finalizePendingAnalysis();
                System.out.println("分析結果がCSVファイルに保存されました。");
            }));

            // ライブチャット感情分析を開始
            // この処理は無限ループで、Ctrl+Cで停止するまで継続される
            analyzer.startLiveChatAnalysis(videoId);
            
        } catch (Exception e) {
            System.err.println("システム開始エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }

     
    //動画IDを取得（ユーザー入力）
    
    private static String getVideoIdFromEnvironment() {
        String videoInput = System.getenv("YOUTUBE_VIDEO_ID");
        if (videoInput == null || videoInput.trim().isEmpty()) {
            throw new RuntimeException(
                "環境変数 'YOUTUBE_VIDEO_ID' が設定されていません。\n" +
                "設定方法:\n" +
                "  Windows: set YOUTUBE_VIDEO_ID=your_video_id_or_url_here\n" +
                "  Mac/Linux: export YOUTUBE_VIDEO_ID=\"your_video_id_or_url_here\"\n" +
                "\n" +
                "動画IDまたはYouTube URLのどちらでも設定できます。\n" +
                "例: set YOUTUBE_VIDEO_ID=dQw4w9WgXcQ\n" +
                "例: set YOUTUBE_VIDEO_ID=https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            );
        }
        
        String videoId = extractVideoId(videoInput.trim());
        System.out.println("環境変数から動画ID取得: " + videoId);
        
        if (videoId == null || videoId.isEmpty()) {
            throw new RuntimeException("有効な動画IDが取得できませんでした: " + videoInput);
        }
        
        return videoId;
    }
    
    /**
     * YouTube URLまたは動画IDから動画IDを抽出
     */
    private static String extractVideoId(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        
        input = input.trim();
        
        // 既に動画IDの形式の場合（11文字の英数字とハイフン・アンダーバー）
        if (input.matches("[a-zA-Z0-9_-]{11}")) {
            return input;
        }
        
        // YouTube URLの場合の各パターンに対応
        String[] patterns = {
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})",
            "v=([a-zA-Z0-9_-]{11})"
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(input);
            if (m.find()) {
                return m.group(1);
            }
        }
        
        // どのパターンにも一致しない場合、そのまま返す（11文字でない場合はエラーになる）
        return input;
    }
}
