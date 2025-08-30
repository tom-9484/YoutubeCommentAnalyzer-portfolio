import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.LiveChatMessageListResponse;
import com.google.api.services.youtube.model.LiveChatMessage;
import com.google.api.services.youtube.model.LiveChatSuperChatDetails;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoLiveStreamingDetails;
import com.google.api.services.youtube.model.VideoListResponse;

//IOException（入出力例外）は、ファイル操作やネットワーク通信などの入出力処理でエラーが発生した際に
//投げられる例外のこと。具体的には、ファイルが見つからない、ネットワークに接続できない、
//ファイルへの書き込み権限がないなどの状況で発生。ファイルが存在しない（FileNotFoundException）
//ファイルへのアクセス権限がない、ネットワーク接続が切断された（SocketException）
//ストリームが閉じている（Stream closed）、入出力操作が中断された（InterruptedIOException）
//その他、入出力に関連するエラー
import java.io.IOException;
import java.util.Arrays;
import java.util.List;


public class SimpleLiveChatReader {

    public void fetchLiveChatComments(YouTube youtube, String videoId) {
        try {
            String liveChatId = getLiveChatId(youtube, videoId);
            if (liveChatId == null) {
                System.out.println("ライブチャットが見つかりませんでした。");
                return;
            }

            String nextPageToken = null;

            while (true) {
                //YouTube クラスの中にある LiveChatMessages クラスの中にある List クラスの変数requestを宣言
                //まず右辺のYoutube型のyoutubeにはYouTubeAuth.getService();が入っていて
                //認証済みの YouTubeクライアントオブジェクトを取得している。
                //そしてyoutube.liveChatMessages()でメソッド実行「ライブチャット関連の操作」をする
                //LiveChatMessagesオブジェクトの list()メソッドを呼び出してる
                //.list()はメッセージ一覧を取得するためのメソッド（GETリクエストを構築）
                //list()の引数はliveChatId→対象動画のID
                //Arrays.asList("snippet", "authorDetails")→取得したい情報のフィールド（メッセージ内容や投稿者情報など）
                YouTube.LiveChatMessages.List request = youtube.liveChatMessages()
                    .list(liveChatId, Arrays.asList("snippet", "authorDetails"));

                    //Arrays.asListはJava のユーティリティメソッドで、配列から固定長のリストを作るためのもの
                    //List<String> parts = new ArrayList<>();
                    //parts.add("snippet");
                    //parts.add("authorDetails");　この3行と同じ意味を持ち
                    //["snippet", "authorDetails"] という 2つの文字列を持つリストを作っている
                    //...aは引数が可変長ということを示してる
                    //リストは固定長、引数は可変長とは？
                    //好きなだけ材料（引数）を渡せるけど、できあがる料理（リスト）はサイズ固定のお弁当箱に詰められる」みたいな感じ
                    //最初に「唐揚げ」「卵焼き」「ブロッコリー」を入れたら、それで固定。
                    //フタが閉まったあとに「やっぱりウインナーも追加したい！」と思っても、追加できない。
                    //でも「卵焼きを明太卵に変更したい」みたいな中身の差し替えはOK。
                    //多分だけどつまり最初にsnippetでメッセージ本文、投稿時刻などの基本情報、authorDetailsで投稿者の表示名、チャンネルID、アイコンURLとかを渡してて、
                    //リストのサイズは最初に渡した引数の２つの要素だから２に決まる
                    //だけど中身は取得するチャット内容とかは当然たくさんコロコロかわるよ、ってことだとおもう。

                if (nextPageToken != null) {
                    request.setPageToken(nextPageToken);
                }

                //GoogleのAPIクライアントライブラリでは、APIリクエストを表すオブジェクト
                //（今回だとrequest変数の中身YouTube.LiveChatMessages.List）に対して .execute() を呼ぶことで
                //HTTPリクエストが送信されて、APIレスポンスが返ってくるように設計されてる。
                //この .execute() メソッドは、ライブラリの中で定義されていて、
                //実際には内部で HTTP 通信を行って、JSONレスポンスを Javaオブジェクトに変換してくれている。
                LiveChatMessageListResponse response = request.execute();
                List<LiveChatMessage> messages = response.getItems();

                
                //LiveChatMessageListResponseの中身
                // {
                //     "kind": "youtube#liveChatMessageListResponse",
                //     "etag": "etag文字列",
                //     "nextPageToken": "次ページ取得用トークン",
                //     "pollingIntervalMillis": 数値（ミリ秒）,
                //     "offlineAt": "ライブ終了日時（ISO8601）",
                //     "pageInfo": {
                //       "totalResults": 総件数,
                //       "resultsPerPage": 1ページあたりの件数
                //     },
                //     "items": [
                //       {
                //         // LiveChatMessage オブジェクト（1件分）
                //       },
                //       ...
                //     ],
                //     "activePollItem": {
                //       // アンケートがある場合の投票データ
                //     }
                //   }

               
                // kindリソースの種類（常に "youtube#liveChatMessageListResponse"）
                // etag	キャッシュ制御用の識別子
                // nextPageToken	次のページを取得するためのトークン（ページネーション）
                // pollingIntervalMillis	次回ポーリングまでの待機時間（ミリ秒）
                // offlineAt	ライブ配信が終了した日時（あれば）
                // pageInfo	ページング情報（総件数・1ページの件数）
                // items	実際のチャットメッセージ一覧（LiveChatMessage の配列）
                // activePollItem	アンケートがある場合の投票メッセージ（省略可）
                  
                //そしてitemsの中にLiveChatMessage オブジェクトがあってそこに
                //チャット内容やユーザー情報とかすぱちゃとかの情報が詰まってる
                //{
                //   "kind": "youtube#liveChatMessage",
                //   "etag": "etag文字列",
                //   "id": "メッセージID",
                //   "snippet": {
                //     "type": "textMessageEvent", // 他にも superChatEvent など
                //     "liveChatId": "チャットID",
                //     "authorChannelId": "投稿者のチャンネルID",
                //     "publishedAt": "投稿日時（ISO8601）",
                //     "hasDisplayContent": true,
                //     "displayMessage": "表示されるメッセージ本文",
                //     "textMessageDetails": {
                //       "messageText": "実際のテキスト本文"
                //     },
                //     // 以下はイベントタイプによって追加される
                //     "superChatDetails": {...},
                //     "superStickerDetails": {...},
                //     "memberMilestoneChatDetails": {...},
                //     "newSponsorDetails": {...},
                //     "fanFundingEventDetails": {...},
                //     "userBannedDetails": {...},
                //     "messageDeletedDetails": {...}
                //   },
                //   "authorDetails": {
                //     "channelId": "投稿者のチャンネルID",
                //     "channelUrl": "チャンネルURL",
                //     "displayName": "表示名",
                //     "profileImageUrl": "アイコン画像URL",
                //     "isVerified": true,
                //     "isChatModerator": false,
                //     "isChatOwner": false,
                //     "isChatSponsor": false
                //   }
                // }

                //とまぁ、こんな大量にいろいろある中で
                //response は LiveChatMessageListResponse 型のオブジェクト
                // .getItems() はその中に含まれる「チャットメッセージの配列（List）」を返すメソッド
                // LiveChatMessage は1つ1つのチャットメッセージを表すオブジェクト
                //つまり「List<LiveChatMessage> messages = response.getItems();」は
                // APIレスポンスの中から、チャットメッセージの一覧（オブジェクト）を取り出して、
                // messages に格納する処理ということ
                

                //ここのmessage : messagesは拡張for文と呼ばれるもの
                //messagesの中にある要素を１件ずつmessageに取り出してる
                //通常のfor文で書くとこうなる
                //for (int i = 0; i < messages.size(); i++) {
                //LiveChatMessage message = messages.get(i);
                //    displayMessage(message);
                //}
                //messagesの中身はさっきぶち込んだメッセージ情報一覧(オブジェクトのやつ)
                //そしてそのあとにdisplayMessage(message)としてるから
                //上記のオブジェクトの中身の
                // "displayMessage": "表示されるメッセージ本文",この要素を表示してる
                //つまりコメント内容を表示してるってこと

                for (LiveChatMessage message : messages) {
                    displayMessage(message);
                }


                //最初のリクエスト. getNextPageToken();から
                //今回は response.getNextPageToken();となっているのは
                //最初の一件は何もない状態だからリクエスト変数に情報くれっていう操作をいれてる
                //そしてAPIから情報を色々取得したのをresponse変数にいれており、
                //そこから次ページへのトークンを取得してるからresponseになってる
                nextPageToken = response.getNextPageToken();

                //getPollingIntervalMillis() は、YouTube側が「次のリクエストは〇〇ミリ秒後にしてね」と指定してる値。
                //そしてその値を interval に代入して,その interval だけ、現在のスレッドを停止（スリープ） 
                //他の処理は止まる（このスレッドが再開するまで）
                long interval = response.getPollingIntervalMillis();
                Thread.sleep(interval);
            }

        } catch (IOException e) {
            System.err.println("ライブチャットIDの取得に失敗しました: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("中断されました。");
        } catch (Exception e) {
            System.err.println(" エラー: " + e.getMessage());
        }
    }
    //catch (InterruptedException e)　→　Thread.sleep()はスレッドが割り込まれたときに
    //InterruptedExceptionを投げるから、try-catch で明示的に受け止めないとコンパイルエラーを吐く
    //catch (Exception e)に関しては他のとこでなんかエラーが起きたときの例外処理
    //getLiveChatId() が null を返して NullPointerException が起きるとか
    //response.getPollingIntervalMillis() が NumberFormatException を投げるとか
    //API通信で IOException や HttpResponseException が起きるとかそんな感じ


    //ここでyoutubeのチャットのIDを取得
    //String：戻り値はチャットID（なければ null）
    //YouTube youtube：YouTube APIのクライアントインスタンス
    //String videoId：対象の動画ID
    //throws IOException：API通信で失敗したときに例外を投げる
    private String getLiveChatId(YouTube youtube, String videoId) throws IOException {
        //動画情報を取得するAPIリクエストを作成。"liveStreamingDetails"を指定することで
        //ライブ配信関連情報を取得する

        //さっきやったチャットの取得とexecuteからの流れは一緒
        //何を取得するか(どこのフィールドから取ってくるか)の指定が
        //snippetから"liveStreamingDetails"に変わっただけ
        YouTube.Videos.List videoRequest = youtube.videos()
            .list(Arrays.asList("liveStreamingDetails"))
            .setId(Arrays.asList(videoId));
            //YouTube APIの videos().list() は、複数の動画IDを渡すことで
            //その動画たちの情報（タイトル、配信時間、チャットIDなど）をまとめて取得できるようになっている
            //つまり：対象の動画のID（例："abc123xyz"）
            //Arrays.asList(...)：複数IDを渡せるようにリスト化（今回は1つだけ）
            //→ 「この動画IDの情報をください」とAPIに伝えているということ

        //ここでAPIリクエストを実行し、レスポンスを取得。
        //JSONレスポンス:items[]をexecuteメソッドでJavaオブジェクト:List<Video>に変換してくれている。(Googleがそう設計してくれてる)
        VideoListResponse videoResponse = videoRequest.execute();
        List<Video> videos = videoResponse.getItems();
        //itemsの中に、動画情報が詰まったオブジェクト（Video）があるからそれを取得

        //動画情報が1件以上あるか確認
        //.isEmpty() は Java に標準で用意されているメソッド。 ただし、使える対象は主にコレクション系のクラス
        //（List, Set, Mapなど）や文字列（String）に定義されている。

        if (videos.isEmpty()) {
            System.out.println("指定された動画が見つかりませんでした。");
            return null;
        }

        if (!videos.isEmpty()) {
            //videos.get(0)：最初の動画情報を取得（今回は最新の1件だけ指定）
            //
            VideoLiveStreamingDetails details = videos.get(0).getLiveStreamingDetails();

            if (details != null) {
                return details.getActiveLiveChatId();
            }
        }
        return null;
    }

    private void displayMessage(LiveChatMessage message) {
        //messageの中身はLiveChatMessageオブジェクト、LiveChatMessage
        //getSnippet() は、LiveChatMessage の中で「メッセージの内容・種類・投稿時間」などを持つサブオブジェクト。
        // getPublishedAt()はgetSnippet() の中にあるオブジェクトでDatatime型の1970年からミリ秒で表した今の日付とかのデータが入ってる
        //toStringRfc3339()はISO 8601に準拠した日時フォーマット
        //2025-08-21T13:09:00+09:00みたいに分かりやすく変換される　+9:00は国際標準から時差9時間
        String time = message.getSnippet().getPublishedAt().toStringRfc3339();
        //message という LiveChatMessage オブジェクトの中にある AuthorDetails オブジェクトを
        //getAuthorDetails() で取得して、その中のプロパティdisplayName（投稿者の表示名）を
        //getDisplayName() で取り出して、それをauthorというString変数に代入している
        String author = message.getAuthorDetails().getDisplayName();
        String text = "";

        switch (message.getSnippet().getType()) {
            //getSnippetはLiveChatMessageSnippetオブジェクトを取得して今まで同様
            //このオブジェクトの中に大量の情報がある。これを階層で掘っていく感じ
            //getType()：その Snippet の中の type（メッセージ種別）を取得できる
            //"textMessageEvent"	通常のチャットメッセージ（テキスト）
            //"superChatEvent"	スーパーチャット（課金付きメッセージ）
            //"superStickerEvent"	スーパーステッカー（課金付きスタンプ）
            //とかがある。
            case "textMessageEvent":
            //message.getSnippet().getType() が "textMessageEvent"の場合
            //LiveChatMessageSnippetオブジェクトの中にあるTextMessageDetailsオブジェクトから
            //MessageText（実際のチャット本文）を取り出して、それをtext変数に代入する処理
                text = message.getSnippet().getTextMessageDetails().getMessageText();
                break;
            case "superChatEvent":
                LiveChatSuperChatDetails sc = message.getSnippet().getSuperChatDetails();
                String comment = sc.getUserComment();
                
                text = "[SC " + sc.getAmountDisplayString() + "] " + (comment != null ? comment : "");
                break;
                //getAmountDisplayString()は金額取得してる
                //getUserComment()がユーザーがスパチャに添えたコメント

            default:
                text = "[" + message.getSnippet().getType() + "]";
        }
                //switch文でcaseにマッチしない場合、最後にdefault:が実行される。
        //つまり、"textMessageEvent" や "superChatEvent" 以外のタイプが来たときの処理。
        //どの case にも当てはまらなかった、でも message.getSnippet().getType() には何かしらのタイプ名が入ってる
        //だからそのタイプ名をそのまま text に表示しておく。
        //具体的なものとしては
        //タイプは"newSponsorEvent","messageDeletedEvent","userBannedEvent"	
        //チャンネルメンバー加入通知　メッセージ削除通知　ユーザーBAN通知あたりがある。


        System.out.printf("[%s] %s: %s%n", time, author, text);
    }
}
//最後のprintfは「format（整形）して print（出力）する」という意味。
// C言語由来の構文で、プレースホルダー（%sなど）に値を埋め込んで文字列を作る。
//[%s] %s: %s%n は 順番に time, author, text に対応してる。
//%nは	OSに応じた改行（\n や \r\n
//%s は「文字列（String）」のプレースホルダー。他にも %d（整数）や %f（小数）などがある
//printf は複雑な整形にも強いから、ログやレポート出力に便利らしい
//いつものSystem.out.printlnでも同じ結果は出せるけど
//printf の方が複雑なフォーマットや数値の桁揃えにも対応できるから、ログ出力やレポート生成ではよく使われるとのこと。
//time = "14:06"; author = "おれ"; text = "[SC ¥1,000] 応援してます！";であれば
//[14:06] おれ: [SC ¥1,000] 応援してます！
//とログにでる。