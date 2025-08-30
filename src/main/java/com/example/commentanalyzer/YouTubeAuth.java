package com.example.commentanalyzer;


import com.google.api.client.auth.oauth2.Credential;
//認証後に得られる認証情報（トークンなど）を保持するクラス。
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
//ローカル環境でOAuth認証を実行するためのユーティリティ。ブラウザを開いて認証する処理を担当。
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
//認証後のリダイレクトを受け取るためのローカルWebサーバー。一時的にPC上で立ち上がる。
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
//認証の流れ・フローを定義するクラス。クライアント情報・スコープ・保存先などをまとめる。
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
//client_secret.json を読み込んで、クライアントIDやシークレットを取得するためのクラス。
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
//Google APIとの通信を行うための「HTTPトランスポート」を初期化するユーティリティ。
import com.google.api.client.http.HttpTransport;
//HTTP通信の抽象クラス。上記の GoogleNetHttpTransport で生成される。
import com.google.api.client.json.JsonFactory;
//JSONを読み書きするためのインターフェース。GoogleのAPIはJSONでやり取りするから必須。cdcd
import com.google.api.client.json.gson.GsonFactory;
//JsonFactory の実装。gsonというライブラリを使ってJSONを処理する。
import com.google.api.client.util.store.FileDataStoreFactory;
//認証トークンをローカルファイルに保存するためのクラス。再認証を避けるために使う。
import com.google.api.services.youtube.YouTube;
//認証後に使う「YouTube APIクライアント」。これがあればAPIを叩ける
import com.google.api.services.youtube.YouTubeScopes;
//YouTube APIで使える「権限（スコープ）」の一覧。今回は読み取り専用を使ってる。
//権限付与でコメントを投稿したり、動画を投稿したりもできる。

import java.io.IOException;
//例外処理用のクラス
import java.io.InputStream;
import java.io.InputStreamReader;
//初めにFileInputStreamクラスのコンストラクタの引数に読み込む「ファイルのパス」を指定。
//次にInputStreamReaderクラスのコンストラクタの引数に「文字エンコードを指定」。
import java.security.GeneralSecurityException;
//セキュリティ関連の処理でエラーが起きたときに使う例外クラス。
//HTTPS通信の初期化（GoogleNetHttpTransport.newTrustedTransport()）で失敗したとき
//暗号化や証明書関連の処理で問題が起きたときに、この例外が投げられる可能性がある。
import java.util.Collections;
//Java のユーティリティクラスで、変更不可のコレクション（リストやセット）を作るための便利メソッドが入ってる。

public class YouTubeAuth {

    private static final String APPLICATION_NAME = "YouTube Live Chat Reader";
    //YouTube APIクライアントの識別名。Google側のログにも使われる。
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    //JSON処理のためのファクトリー。Gsonを使う設定。
    private static final String CREDENTIALS_FOLDER = "credentials";
    //認証トークンを保存するフォルダ名。初回認証後に自動生成される。
    private static final String CLIENT_SECRETS_FILE = "/client_secret.json";
    //認証に使う秘密鍵ファイルのパス。resources フォルダに置く場合は / 付きでOK。

    //認証を実行して、YouTube APIクライアントを返すメソッド。
    public static YouTube getService() throws IOException, GeneralSecurityException {
        //安全な通信を行うためのHTTPトランスポートを初期化。
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        //認証処理を実行して、認証済みのトークン情報を取得。
        Credential credential = authorize(httpTransport);
        //認証済みの YouTube クライアントを構築して返す。
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    //認証処理の本体。client_secret.json を読み込んで、認証フローを構築する。
    private static Credential authorize(HttpTransport httpTransport) throws IOException {
        //client_secret.json をクラスパスから読み込んでる
        InputStream inputStream = YouTubeAuth.class.getResourceAsStream(CLIENT_SECRETS_FILE);
        if (inputStream == null) {
            //ファイルが見つからなかったらエラーを出す。
            throw new IOException("client_secret.jsonファイルが見つかりません。src/main/resources/ フォルダに配置してください。");
        }


        //JSONファイルを読み込んで、クライアント情報をオブジェクト化。
        //この1行は、「JSONを読み込んで、GoogleClientSecretsのオブジェクトを作る」という目的
        //だが3つの処理がネストされてる。
        //inputStreamは生のデータ、new InputStreamReader(inputStream)でバイト→文字列変換してJSONテキストとして認識できるようにする
        //GoogleClientSecrets.load(JSON_FACTORY, reader)
        //ここで、JSONをJavaオブジェクトに変換する処理。JSON_FACTORY（GsonFactoryなど）が、JSONの構造を解析して、GoogleClientSecrets クラスにマッピングしてくれる。
        //これらを分けて書くとこうなる。
        //Reader reader = new InputStreamReader(inputStream);
        //GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets,
                Collections.singletonList(YouTubeScopes.YOUTUBE_READONLY))
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(CREDENTIALS_FOLDER)))
                .setAccessType("offline")
                .build();
        //認証フローを構築。
        //new GoogleAuthorizationCodeFlow.Builder(...)の引数は
        //httpTransport(HTTPSなどGoogle APIとの通信を行うための設定), JSON_FACTORY(GsonやJacksonなどJSONの読み書き方法）, 
        //clientSecrets(client_secret.json から読み込んだクライアント情報), 
        //Collections.singletonList(YouTubeScopes.YOUTUBE_READONLY)→認証で使うスコープ（権限）をリストで渡す。
        //今回は読み取り専用。これらの引数で４つ。
        //.setDataStoreFactory(...)
        //これは「認証トークンをどこに保存するか」を指定してる。
        // credentials/ フォルダに保存することで、次回以降の認証を省略できるようになる。
        //.setAccessType("offline")
        //これは「リフレッシュトークンもください」とGoogleにお願いする設定。
        //"online" → 一時的なアクセストークンだけ　"offline" → アクセストークン＋リフレッシュトークン（長期的に使える）
        //これがあると、再認証なしでAPIを使い続けられるようになる！
        //.build()
        //最後に .build() で、全部の設定をまとめて GoogleAuthorizationCodeFlow オブジェクトを完成させる。
        
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
}
        //今まで準備してきた flow を使って実際にユーザー認証を走らせ、認証済みのクレデンシャル（資格情報）を取得する処理。
        //ローカルサーバーを立てて、ブラウザ認証を実行。認証済みトークンを返す。
        //AuthorizationCodeInstalledApp	ローカル環境で動くアプリ用のOAuth認証を処理するクラス
        //new LocalServerReceiver()	認証時にローカルサーバーを立てて、Googleからのリダイレクトを受け取る
        //.authorize("user")
        //このメソッドが、実際に認証を開始する部分。
        //"user" は識別子で、保存されるトークンに紐づく名前（複数ユーザー対応も可能）
        //認証が成功すると、Credential オブジェクトが返ってくる
        //この Credential を使って、YouTube API にアクセスできるようになる。