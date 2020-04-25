package com.example.bookshelf;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashMap;

public class BookListFragment extends Fragment {

    private static final String BOOK_LIST_KEY = "booklist";
    private ArrayList<Book> books;
    private ListView listView;

    private BookSelectedInterface parentActivity;

    public BookListFragment() {}

    public static BookListFragment newInstance(ArrayList<Book> books) {
        BookListFragment fragment = new BookListFragment();
        Bundle args = new Bundle();

        /*
         A Book implements the Parcelable interface
         therefore we can place an arraylist of books
         inside a bundle by using that put() method.
         */
        args.putParcelableArrayList(BOOK_LIST_KEY, books);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        /*
         This fragment needs to communicate with its parent activity
         so we verify that the activity implemented our known interface
         */
        if (context instanceof BookSelectedInterface) {
            parentActivity = (BookSelectedInterface) context;
        } else {
            throw new RuntimeException("Please implement the required interface(s)");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        books = new ArrayList<Book>();
        if (getArguments() != null) {
            books.addAll((ArrayList) getArguments().getParcelableArrayList(BOOK_LIST_KEY));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        listView = (ListView) inflater.inflate(R.layout.fragment_book_list, container, false);

        listView.setAdapter(new BooksAdapter(getContext(), books));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                parentActivity.bookSelected(position);
            }
        });

        return listView;
    }

    public void updateBooksDisplay(ArrayList<Book> books) {
        this.books.clear();
        this.books.addAll(books);
        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
    }

    /*
    Interface for communicating with attached activity
     */
    interface BookSelectedInterface {
        void bookSelected(int index);
    }
}
