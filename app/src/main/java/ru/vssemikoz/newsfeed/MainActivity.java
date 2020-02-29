package ru.vssemikoz.newsfeed;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ru.vssemikoz.newsfeed.dao.NewsItemDAO;
import ru.vssemikoz.newsfeed.adapters.NewsFeedAdapter;
import ru.vssemikoz.newsfeed.dialogs.PickCategoryDialog;
import ru.vssemikoz.newsfeed.models.Category;
import ru.vssemikoz.newsfeed.models.NewsApiResponseItem;
import ru.vssemikoz.newsfeed.models.NewsApiResponse;
import ru.vssemikoz.newsfeed.models.NewsItem;
import ru.vssemikoz.newsfeed.storage.IconicStorage;
import ru.vssemikoz.newsfeed.storage.NewsApiRepository;
import ru.vssemikoz.newsfeed.storage.NewsStorage;

public class MainActivity extends AppCompatActivity implements PickCategoryDialog.OnCategorySelectedListener {
    private String TAG = MainActivity.class.getSimpleName();
    private boolean showOnlyFavoriteNews = false;
    private Category category = Category.ALL;
    private MainApplication mainApplication;
    private NewsStorage newsStorage;
    private Callback<NewsApiResponse> callbackNewsItemList;
    private List<NewsItem> news = new ArrayList<>();
    private NewsFeedAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainApplication = (MainApplication) getApplicationContext();

        ImageButton categoryButton = findViewById(R.id.ib_category);
        ImageButton favoriteNewsButton = findViewById(R.id.ib_favorite);
        favoriteNewsButton.setImageDrawable(IconicStorage.getWhiteStarBorderless(this));

        categoryButton.setOnClickListener(v -> {
            DialogFragment categoryDialog = new PickCategoryDialog();
            categoryDialog.show(getSupportFragmentManager(), "categoryDialog");
        });
        favoriteNewsButton.setOnClickListener(v -> {
            showOnlyFavoriteNews = !showOnlyFavoriteNews;
            Log.d(TAG, "onCreate: " + showOnlyFavoriteNews);
            news = getNewsFromDB();
            changeFavoriteIcon(favoriteNewsButton);
            adapter.setNewsList(news);
            adapter.notifyDataSetChanged();
        });
        updateCategoryNameOnToolBar();
        initRecView();
        initNewsStorage();
        initNewsItemListCallback();
        performCall();
    }

    private void changeFavoriteIcon(ImageButton button) {
        if (showOnlyFavoriteNews) {
            button.setImageDrawable(IconicStorage.getYellowStarBorderless(this));
        } else {
            button.setImageDrawable(IconicStorage.getWhiteStarBorderless(this));
        }
    }

    private List<NewsItem> getNewsFromDB() {
        return newsStorage.getNewsFromDB(showOnlyFavoriteNews, category);
    }

    private void initRecView() {
        recyclerView = findViewById(R.id.rv_news_feed);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NewsFeedAdapter(getApplicationContext());
        adapter.setOnItemClickListener(new NewsFeedAdapter.onItemClickListener() {
            @Override
            public void onChangeFavoriteStateClick(int position) {
                changeFavoriteState(position);
            }

            @Override
            public void onNewsImageClick(int position) {
                showNewsInBrowserByUrl(position);
            }
        });
    }

    private void changeFavoriteState(int position) {
        NewsItem item = news.get(position);
        item.invertFavoriteState();
        newsStorage.updateNews(item);
        Log.d(TAG, "changeFavoriteState: " + item.isFavorite());
        if (!item.isFavorite() && showOnlyFavoriteNews) {
            news.remove(position);
            adapter.notifyItemRemoved(position);
        } else {
            adapter.notifyItemChanged(position);
        }
    }

    private void showNewsInBrowserByUrl(int position) {
        NewsItem item = news.get(position);
        String url = item.getUrl();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage("com.android.chrome");
        try {
            getApplicationContext().startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            intent.setPackage(null);
            getApplicationContext().startActivity(intent);
        }
    }

    private void initRecycleViewData() {
        news = getNewsFromDB();
        adapter.setNewsList(news);
        recyclerView.setAdapter(adapter);
        Toast.makeText(getApplicationContext(),
                "DBSize: " + news.size(),
                Toast.LENGTH_LONG).show();
    }

    private void performCall() {
        NewsApiRepository newsApiRepository = new NewsApiRepository(mainApplication);
        newsApiRepository.getNewsFromApi(category, callbackNewsItemList);
    }

    private void initNewsStorage() {
        NewsItemDAO newsItemDAO = mainApplication.getNewsDataBase().newsItemDAO();
        newsStorage = new NewsStorage(newsItemDAO);
    }

    private void initNewsItemListCallback() {
        callbackNewsItemList = new Callback<NewsApiResponse>() {
            @Override
            public void onResponse(Call<NewsApiResponse> call, Response<NewsApiResponse> response) {
                if (!response.isSuccessful()) {
                    Log.d("MyLog", "onResponse " + response.code());
                    return;
                }
                newsStorage.insertUnique(getNewsItemListByResponse(response, category));
                initRecycleViewData();
            }

            @Override
            public void onFailure(Call<NewsApiResponse> call, Throwable t) {
                Log.d("MyLog", "onFailure " + Objects.requireNonNull(t.getMessage()));
            }
        };
    }

    private List<NewsItem> getNewsItemListByResponse(Response<NewsApiResponse> response, Category category) {
        List<NewsItem> news = new ArrayList<>();
        List<NewsApiResponseItem> newsApiResponseItems = Objects.requireNonNull(response.body()).getNewsApiResponseItemList();
        for (NewsApiResponseItem newsApiResponseItem : newsApiResponseItems) {
            news.add(new NewsItem(newsApiResponseItem, category));
        }
        return news;
    }

    @Override
    public void onCategorySelected(Category selectCategory) {
        category = selectCategory;
        performCall();
        updateCategoryNameOnToolBar();
    }

    private void updateCategoryNameOnToolBar() {
        TextView categoryTextView = findViewById(R.id.tv_category);
        categoryTextView.setText(Category.getCategoryName(category));
    }
}
