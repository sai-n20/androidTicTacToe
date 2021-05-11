package com.example.project4;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    //Initialize thread and handlers
    Thread cross = new Thread(new T1());
    Thread circle = new Thread(new T2());

    public static Handler t1Handler;
    public static Handler t2Handler;

    //This bundle will house the board state input string for the MinMax algorithm
    Bundle data = new Bundle();

    private Button[][] buttons = new Button[3][3];
    private TextView gameStatusText;
    private Button startButtonText;

    public char X = 'X';
    public char O = 'O';
    public String boardStatus = "b b b b b b b b b";

    public Node nextMove;
    public String nextMoveString = "";
    public boolean gameBegin = false;
    public boolean gameRestarted = false;
    public static final int CROSS_PLAYED = 0;
    public static final int CIRCLE_PLAYED = 1;
    public static final int GAME_OVER = 99;
    public static final int GAME_NOT_OVER = 2;
    public static final int GAME_ALMOST_OVER = 3;
    public static final int STOP_PLAYING = 4;

    //UI thread handler
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            int what = msg.what;
            Message newMessage = Message.obtain(msg);
            switch (what) {

                //X just played, time to update board, check game status and send signal to play to O incase game is still on
                case CROSS_PLAYED:
                    updateBoard(newMessage.getData().getString("board").replaceAll("\\s+",""));
                    if(newMessage.arg1 == GAME_OVER) {
                        gameRestarted = false;

                        //Suicide messages for the 2 handlers
                        Message stopMsg1 = new Message();
                        Message stopMsg2 = new Message();
                        stopMsg1.what = STOP_PLAYING;
                        stopMsg2.what = STOP_PLAYING;
                        t1Handler.sendMessage(stopMsg1);
                        t2Handler.sendMessage(stopMsg2);

                        if(newMessage.arg2 == 10)
                            gameStatusText.setText("Game over, X wins.");
                        else if(newMessage.arg2 == -10)
                            gameStatusText.setText("Game over, O wins.");
                        else
                            gameStatusText.setText("Game over, Tie.");
                    }
                    else {
                        t2Handler.sendMessage(newMessage);
                    }
                    break;

                //O just played, update board and send status to X's thread
                case CIRCLE_PLAYED:
                    updateBoard(newMessage.getData().getString("board").replaceAll("\\s+",""));
                    t1Handler.sendMessage(newMessage);
                    break;

                //Game has been restarted with the reset button, clear their message queues
                case STOP_PLAYING:
                    gameRestarted = true;
                    t1Handler.removeCallbacksAndMessages(null);
                    t2Handler.removeCallbacksAndMessages(null);
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gameStatusText = findViewById(R.id.gameStatus);
        startButtonText = findViewById(R.id.startButton);

        //Create 2D buttons array for fast access while updating display
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String buttonID = "button_" + i + j;
                int resID = getResources().getIdentifier(buttonID, "id", getPackageName());
                buttons[i][j] = findViewById(resID);
            }
        }
    }

    public void onStartButton(View v) {

        //Thread will be dead if the game has ended and during initial run
        if(!cross.isAlive()) {
            cross = new Thread(new T1());
            circle = new Thread(new T2());
            cross.start();
            circle.start();
        }

        //Extra actions to be taken when the game has been restarted
        if(gameBegin) {

            //Avoiding certain race conditions
            if(!gameStatusText.getText().toString().startsWith("Game over")) {
                Message stopMsg = new Message();
                stopMsg.what = STOP_PLAYING;
                mHandler.sendMessage(stopMsg);
            }
            Toast toast = Toast.makeText(getApplicationContext(), "Restarting the game.", Toast.LENGTH_SHORT);
            toast.show();
            boardStatus = "b b b b b b b b b";
        }
        gameBegin = true;
        gameStatusText.setText("Game has started.");
        startButtonText.setText("Reset");

        //Send bootstrap message to UI thread to kick off the game
        Message msg = new Message();
        data.putString("board", boardStatus);
        msg.setData(data);
        msg.what = CIRCLE_PLAYED;
        mHandler.sendMessage(msg);
    }

    //MinMax algorithm returns String[]. Converting to String using this procedure
    private static String stringArrToString(String[] stringArray, String delim) {
        StringBuilder sb = new StringBuilder();
        for (String str: stringArray)
            sb.append(str).append(delim);
        return sb.substring(0, sb.length() - 1);
    }

    //Repeated procedure to update the board
    private void updateBoard(String board) {
        int temp = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if(board.charAt(temp) == X) {
                    buttons[i][j].setText(R.string.cross);
                }
                else if(board.charAt(temp) == O) {
                    buttons[i][j].setText(R.string.circle);
                }
                else {
                    buttons[i][j].setText("");
                }
                temp++;
            }
        }
    }

    //This is the thread belonging to player 'X'
    public class T1 implements Runnable  {

        public void run() {

            //Initialize looper and handler
            Looper.prepare();
            t1Handler = new Handler(Looper.myLooper()) {
                    public void handleMessage(Message msg) {
                        Message newMessage = Message.obtain(msg);

                        //Suicide message
                        if(newMessage.what == STOP_PLAYING) {
                            Looper looper = Looper.myLooper();
                            looper.quit();
                            return;
                        }
                        try { Thread.sleep(1000); }
                        catch (InterruptedException e) { System.out.println("Thread interrupted!") ; }

                        //Check if game has been restarted while this thread just received a message
                        if(gameRestarted) {
                            gameRestarted = false;
                            return;
                        }

                        //Compute next move by using provided MinMax algorithm
                        AI_MinMax startGame = new AI_MinMax(newMessage.getData().getString("board"));
                        nextMove = startGame.movesList.get(0);
                        nextMoveString = stringArrToString(nextMove.getInitStateString(), " ");

                        //Use MinMax logic to set message args
                        if((nextMove.getMinMax() == 10 || nextMove.getMinMax() == -10) && newMessage.arg1 == GAME_ALMOST_OVER) {
                            newMessage.arg1 = GAME_OVER;
                            gameRestarted = false;
                            newMessage.arg2 = nextMove.getMinMax();
                        }
                        else if((nextMove.getMinMax() == 10 || nextMove.getMinMax() == -10) && newMessage.arg1 == GAME_NOT_OVER) {
                            newMessage.arg1 = GAME_ALMOST_OVER;
                            newMessage.arg2 = nextMove.getMinMax();
                        }
                        else{
                            newMessage.arg1 = GAME_NOT_OVER;
                            newMessage.arg2 = GAME_NOT_OVER;
                        }
                        Bundle data = new Bundle();
                        data.putString("board", nextMoveString);
                        newMessage.setData(data);
                        newMessage.what = CROSS_PLAYED;
                        mHandler.sendMessage(newMessage);
                    }
            };
            Looper.loop();
        }
    }

    //This is the thread belonging to player 'O'
    public class T2 implements Runnable  {

        public void run() {

            Looper.prepare();
            t2Handler = new Handler(Looper.myLooper()) {
                public void handleMessage(Message msg) {
                    Message newMessage = Message.obtain(msg);

                    //Suicide message
                    if(newMessage.what == STOP_PLAYING) {
                        Looper looper = Looper.myLooper();
                        looper.quit();
                        return;
                    }
                    try { Thread.sleep(1000); }
                    catch (InterruptedException e) { System.out.println("Thread interrupted!") ; }
                    String boardString = newMessage.getData().getString("board");

                    //O is not the best player at this game..
                    if(boardString.contains("b")) {
                        boardString = boardString.replaceFirst("b", "O");
                    }
                    Bundle data = new Bundle();
                    data.putString("board", boardString);
                    newMessage.setData(data);
                    newMessage.what = CIRCLE_PLAYED;
                    if(gameRestarted) {
                        gameRestarted = false;
                        return;
                    }
                    mHandler.sendMessage(newMessage);
                }
            };
            Looper.loop();
        }
    }
}

