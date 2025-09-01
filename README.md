# YouTubeCommentAnalyzer-java portfolio
#### 概要
youtubeのライブ配信コメントをリアルタイムで収集  
マイクロソフトのAzure AI LanguageのAPIを使用し収集したコメントを点数付けし  
0.3点より大きければポジティブ-0.3点より小さければネガティブとする感情分析を行う  
その計測結果をcsvファイルで出力するプログラム  
virtual youtuberの長時間配信のコメントの様子を自動で収集し  
配信の切り抜き動画編集の効率を大幅に上げるために制作  
元々自分のために作ったものだが、vtuberだけでなく何かしら超長時間配信の切り抜き動画編集のために使用可能  
#### 開発期間
22日・約210時間
#### 使用技術
言語:Java（JDK21）＋ Maven（依存管理）  
API利用時の認証:OAuth2.0認証（youtube api利用時）  
使用API:YouTube Data API v3, Microsoft Azure AI Language–Sentiment Analysis API  
サーバー:AWS EC2 (OS:Ubuntu)  
Git GitHub
#### 大変だったこと
1.メインのコメント収集プログラムを作る以前のOAtuth2.0認証が何をすればいいのか分からず樹海に放り出される  

2.コメント収集のプログラムはPythonでサンプルコードがたくさん転がっているから  
　何とかなるだろうと思ってみたら無限にメソッドが出てきて何がなんだかわからない  
　それらをほどきながらjavaに書き換えなければならなかった  
  
3.無限にメソッドが出てくる理由がyoutube apiに色々仕様がたくさんあったからであり  
　youtube api使うためにわかりにくい公式ドキュメントを読まなければならなかった  
 
4.コードを書く上でjavaやGoogleが用意してるパッケージをimportとしなければならず  
　何を使えばいいのか五里霧中の迷子  
  
5.mavenってなに？依存関係ってなに？？からpom.xmlファイルを作成しなければならなかった  

6.filterやcollect、map、データの流れであるストリームに変換する.stream()など  
　Javaに備えられたStream APIという機能を理解しなければならなかった  
  
7.Youtube APIを使う時にGoogle が公式で提供しているクライアントライブラリの  
　Google API Client Library for Javaを使ったが  
　この中に用意されているYoutubeクラスの構造を把握しなければならず  
　「YouTubeクラス」の内部クラスである「LiveChatMessagesクラスのlist()メソッド」を呼び出すと、  
　LiveChatMessageListResponseオブジェクトが返され、それはitems[] 配列と示され  
　その0番目に格納されたLiveChatMessageオブジェクトが格納されており  
　そのLiveChatMessageの中のsnippetオブジェクトの中に  
　プロパティ(コメント本文や投稿時刻などのメッセージ情報の文字列)が存在する  
　といったことを理解しなければいけなかった  
   
8.youtubeコメント収集を乗り越えた先に今度はazure apiの使用ということで構造把握地獄で一度心が折れた  

9.Azure APIではライブラリを使わずにHTTPベースのREST APIを直接叩いたため  
　HttpRequestクラスで .uri .header .POST .build()といったメソッドを連打し  
　httpClientで.sendすることの理解が必要だった  

10.感情分析した結果を表示をするときの処理をどうすべきか検索した結果  
　データのストリーム変換とラムダ式を使うということで頭が爆発した

11.Azure APIリクエスト用のJSONデータを作成する必要があったためJSONライブラリを知る必要があった
