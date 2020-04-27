package com.example.bookshelf;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import edu.temple.audiobookplayer.AudiobookService;

public class MainActivity extends AppCompatActivity implements BookListFragment.BookSelectedInterface, BookDetailsFragment.PlayButtonPressedInterface {

    private static final String BOOKS_KEY = "books";
    private static final String SELECTED_BOOK_KEY = "selectedBook";
    private static final String CURRENT_BOOK_KEY = "currentBook";
    private static final String SAVED_PROGRESS_KEY = "savedProgress";
    private static final String NOW_PLAYING_KEY = "nowPlaying";

    FragmentManager fm;

    boolean twoPane;
    BookListFragment bookListFragment;
    BookDetailsFragment bookDetailsFragment;

    ArrayList<Book> books;
    RequestQueue requestQueue;
    Book selectedBook;

    EditText searchEditText;

    Button pauseButton, stopButton;
    SeekBar seekBar;

    //Handler messageHandler = new ProgressHandler(this);
    String currentBookName;
    int savedProgress, time, bookId;
    String nowPlayingText;
    TextView nowPlayingTextView;

    Intent bindIntent;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message message) {
            super.handleMessage(message);

            AudiobookService.BookProgress progress = (AudiobookService.BookProgress) message.obj;

            if (progress != null) {
                time = progress.getProgress();
                bookId = progress.getBookId();
                seekBar.setMax(selectedBook.getDuration());
                seekBar.setProgress(progress.getProgress());

            }
        }
    };

    AudiobookService.MediaControlBinder binder;
    boolean connected;
    AudiobookService audiobookService;
    ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (AudiobookService.MediaControlBinder)service;
            connected = true;
            binder.setProgressHandler(handler);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connected = false;
        }
    };

    private final String SEARCH_API = "https://kamorris.com/lab/abp/booksearch.php?search=";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchEditText = findViewById(R.id.searchEditText);

        /*
        Perform a search
         */
        findViewById(R.id.searchButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchBooks(searchEditText.getText().toString());
            }
        });

         bindIntent = new Intent(MainActivity.this, AudiobookService.class);
        bindService(bindIntent, connection, BIND_AUTO_CREATE);
        pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(connected) {
                    binder.pause();
                    if(pauseButton.getText() == "Pause") {
                        pauseButton.setText("Unpause");
                    }
                    else {
                        pauseButton.setText("Pause");
                    }
                }
            }
        });

        stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(connected) {
                    binder.stop();
                    seekBar.setProgress(0);
                    nowPlayingText = "";
                    nowPlayingTextView = findViewById(R.id.nowPlaying);
                    nowPlayingTextView.setText(nowPlayingText);
                    stopService(bindIntent);
                }
            }
        });

        seekBar = findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(connected) {
                    if(fromUser) {
                        binder.seekTo(progress);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        /*
        If we previously saved a book search and/or selected a book, then use that
        information to set up the necessary instance variables
         */
        if (savedInstanceState != null) {
            books = savedInstanceState.getParcelableArrayList(BOOKS_KEY);
            selectedBook = savedInstanceState.getParcelable(SELECTED_BOOK_KEY);
            currentBookName = savedInstanceState.getString(CURRENT_BOOK_KEY);
            savedProgress = savedInstanceState.getInt(SAVED_PROGRESS_KEY);
            nowPlayingText = savedInstanceState.getString(NOW_PLAYING_KEY);
            nowPlayingTextView = findViewById(R.id.nowPlaying);
            nowPlayingTextView.setText(nowPlayingText);
        }
        else
            books = new ArrayList<Book>();

        twoPane = findViewById(R.id.container2) != null;
        fm = getSupportFragmentManager();

        requestQueue = Volley.newRequestQueue(this);

        /*
        Get an instance of BookListFragment with an empty list of books
        if we didn't previously do a search, or use the previous list of
        books if we had previously performed a search
         */
        bookListFragment = BookListFragment.newInstance(books);

        fm.beginTransaction()
                .replace(R.id.container1, bookListFragment)
        .commit();

        /*
        If we have two containers available, load a single instance
        of BookDetailsFragment to display all selected books.

        If a book was previously selected, show that book in the book details fragment
        *NOTE* we could have simplified this to a single line by having the
        fragment's newInstance() method ignore a null reference, but this way allow
        us to limit the amount of things we have to change in the Fragment's implementation.
         */
        if (twoPane) {
            if (selectedBook != null)
                bookDetailsFragment = BookDetailsFragment.newInstance(selectedBook);
            else
                bookDetailsFragment = new BookDetailsFragment();

            fm.beginTransaction()
                    .replace(R.id.container2, bookDetailsFragment)
                    .commit();
        } else {
            if (selectedBook != null) {
                fm.beginTransaction()
                        .replace(R.id.container1, BookDetailsFragment.newInstance(selectedBook))
                        // Transaction is reversible
                        .addToBackStack(null)
                        .commit();
            }
        }
    }

    /*
    Fetch a set of "books" from from the web service API
     */
    private void fetchBooks(String searchString) {
        /*
        A Volloy JSONArrayRequest will automatically convert a JSON Array response from
        a web server to an Android JSONArray object
         */
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(SEARCH_API + searchString, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                if (response.length() > 0) {
                    books.clear();
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject bookJSON;
                            bookJSON = response.getJSONObject(i);
                            books.add(new Book (bookJSON.getInt(Book.JSON_ID),
                                    bookJSON.getString(Book.JSON_TITLE),
                                    bookJSON.getString(Book.JSON_AUTHOR),
                                    bookJSON.getString(Book.JSON_COVER_URL),
                                    bookJSON.getInt(Book.JSON_DURATION)));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    updateBooksDisplay();
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.search_error_message), Toast.LENGTH_SHORT).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        requestQueue.add(jsonArrayRequest);
    };

    private void updateBooksDisplay() {
        /*
        Remove the BookDetailsFragment from the container after a search
        if it is the currently attached fragment
         */
        if (fm.findFragmentById(R.id.container1) instanceof BookDetailsFragment)
            fm.popBackStack();
        bookListFragment.updateBooksDisplay(books);
    }

    @Override
    public void bookSelected(int index) {
        selectedBook = books.get(index);
        if (twoPane)
            /*
            Display selected book using previously attached fragment
             */
            bookDetailsFragment.displayBook(selectedBook);
        else {
            /*
            Display book using new fragment
             */
            fm.beginTransaction()
                    .replace(R.id.container1, BookDetailsFragment.newInstance(selectedBook))
                    // Transaction is reversible
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save previously searched books as well as selected book
        outState.putParcelableArrayList(BOOKS_KEY, books);
        outState.putParcelable(SELECTED_BOOK_KEY, selectedBook);
        outState.putString(CURRENT_BOOK_KEY, currentBookName);
        outState.putInt(SAVED_PROGRESS_KEY, savedProgress);
        outState.putString(NOW_PLAYING_KEY, nowPlayingText);
    }

    @Override
    public void playButtonPressed(Book book) {
        if(connected) {
            startService(bindIntent);
            if(binder.isPlaying()) {
                binder.stop();
            }
            seekBar.setMax(book.getDuration());
            binder.play(book.getId());
            currentBookName = book.getTitle();
            nowPlayingText = "Now Playing: " + book.getTitle();
            nowPlayingTextView = findViewById(R.id.nowPlaying);
            nowPlayingTextView.setText(nowPlayingText);
        }
    }

    /*private static class ProgressHandler extends Handler {
        MainActivity parentActivity;
        public static int time;
        public static int bookId;
        public static String bookName;

        private ProgressHandler(Context activity) {
            super();
            this.parentActivity = (MainActivity)activity;
        }

        @Override
        public void handleMessage(@NonNull Message message) {
            super.handleMessage(message);

            AudiobookService.BookProgress progress = (AudiobookService.BookProgress) message.obj;

            if(parentActivity != null && progress != null) {
                time = progress.getProgress();
                bookId = progress.getBookId();
                parentActivity.seekBar.setProgress(progress.getProgress());

            }
        }
    }*/

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }
}
