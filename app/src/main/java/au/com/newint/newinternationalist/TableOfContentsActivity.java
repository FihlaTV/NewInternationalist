package au.com.newint.newinternationalist;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.gson.JsonArray;

import org.apache.http.HttpResponse;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import au.com.newint.newinternationalist.util.IabException;
import au.com.newint.newinternationalist.util.IabHelper;
import au.com.newint.newinternationalist.util.IabResult;
import au.com.newint.newinternationalist.util.Inventory;
import au.com.newint.newinternationalist.util.Purchase;


public class TableOfContentsActivity extends ActionBarActivity {

    static Issue issue;
    static IabHelper mHelper;
    static Inventory inventory = null;
    static ArrayList<Purchase> purchases = null;

    static ArrayList<Object> layoutList = null;

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Dispose of the in-app helper
        if (mHelper != null) mHelper.dispose();
        mHelper = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table_of_contents);
        final Fragment tableOfContentsFragment = new TableOfContentsFragment();

        issue = getIntent().getParcelableExtra("issue");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, tableOfContentsFragment)
                    .commit();
        }

        // Set title to Home screen
        setTitle("Home");

        // Setup in-app billing
        mHelper = Helpers.setupIabHelper(getApplicationContext());

        if (!Helpers.emulator) {
            // Only startSetup if not running in an emulator
            mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                public void onIabSetupFinished(IabResult result) {
                    if (!result.isSuccess()) {
                        // Oh noes, there was a problem.
                        Log.d("TOC", "Problem setting up In-app Billing: " + result);
                    }
                    // Hooray, IAB is fully set up!
                    Log.i("TOC", "In-app billing setup result: " + result);

                    // Make a products list of the subscriptions and this issue
                    final ArrayList<String> skuList = new ArrayList<String>();
                    skuList.add(Helpers.TWELVE_MONTH_SUBSCRIPTION_ID);
                    skuList.add(Helpers.ONE_MONTH_SUBSCRIPTION_ID);
                    skuList.add(Helpers.singleIssuePurchaseID(issue.getNumber()));

                    try {
                        purchases = new ArrayList<>();
                        inventory = mHelper.queryInventory(true, skuList);
                        // Is there a subscription purchase in the inventory?
                        for (String sku : skuList) {
                            Purchase purchase = inventory.getPurchase(sku);
                            if (purchase != null) {
                                purchases.add(purchase);
                            }
                        }
                    } catch (IabException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_table_of_contents, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case android.R.id.home:
                // Handles a back/up button press and returns to previous Activity
                finish();
                return true;
            case R.id.menu_item_share:
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                // Send issue share information here...
                DateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
                String magazineInformation = issue.getTitle()
                        + " - New Internationalist magazine "
                        + dateFormat.format(issue.getRelease());
                shareIntent.putExtra(Intent.EXTRA_TEXT, "I'm reading "
                                + magazineInformation
                                + ".\n\n"
                                + issue.getWebURL()
                );
                shareIntent.setType("text/plain");
                // TODO: When time permits, save the image to externalStorage and then share.
//                shareIntent.putExtra(Intent.EXTRA_STREAM, issue.getCoverUriOnFilesystem());
//                shareIntent.setType("image/jpeg");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "New Internationalist magazine, " + dateFormat.format(issue.getRelease()));
                startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.action_share_toc)));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class TableOfContentsFragment extends Fragment {

        public TableOfContentsFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final View rootView = inflater.inflate(R.layout.fragment_table_of_contents, container, false);

            final RecyclerView recList = (RecyclerView) rootView.findViewById(R.id.card_list);
            recList.setHasFixedSize(true);
            LinearLayoutManager llm = new LinearLayoutManager(rootView.getContext());
            llm.setOrientation(LinearLayoutManager.VERTICAL);
            recList.setLayoutManager(llm);

            final TableOfContentsAdapter adapter = new TableOfContentsAdapter(issue);
            recList.setAdapter(adapter);

            layoutList = new ArrayList<Object>();

            if (issue != null) {
                issue.preloadArticles(new CacheStreamFactory.CachePreloadCallback() {
                    @Override
                    public void onLoad(byte[] payload) {
                        // Articles have preloaded, so sort them into the layoutList
                        ArrayList<Article> articles = issue.getArticles();

                        populateLayoutListFromArticles(articles);

                        adapter.notifyItemChanged(1);
                        Log.i("PreloadArticles", "Articles ready, so refreshing first article.");
                    }

                    @Override
                    public void onLoadBackground(byte[] payload) {

                    }
                });
            }

            // Register for the listener when the zip file has downloaded and unzipped
            Publisher.IssueZipDownloadCompleteListener issueZipDownloadCompleteListener = new Publisher.IssueZipDownloadCompleteListener() {

                @Override
                public void onIssueZipDownloadComplete(ArrayList responseList) {
                    Log.i("TOC", "Received listener, handling zip download response.");
                    // Check response, and respond with dialog

                    ProgressBar progressSpinner = (ProgressBar) rootView.findViewById(R.id.toc_zip_loading_spinner);
                    progressSpinner.setVisibility(View.GONE);

                    // TODO: loop through responses instead of just getting the first one...

                    final HttpResponse response;
                    int responseStatusCode;

                    if (responseList != null && responseList.size() > 0) {
                        response = (HttpResponse) responseList.get(0);
                    } else {
                        response = null;
                    }

                    if (response != null) {
                        responseStatusCode = response.getStatusLine().getStatusCode();

                        if (responseStatusCode >= 200 && responseStatusCode < 300) {
                            // The zip downloaded and completed!
                            // Alert the user that it was a success!
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setMessage(R.string.zip_download_success_dialog_message).setTitle(R.string.zip_download_success_dialog_title);
                            builder.setPositiveButton(R.string.zip_download_dialog_ok_button, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked OK button
                                }
                            });
                            AlertDialog dialog = builder.create();
                            dialog.show();

                        } else if (responseStatusCode > 400 && responseStatusCode < 500) {
                            // Article request failed
                            Log.i("TOC", "Zip download failed with code: " + responseStatusCode);
                            // Alert and intent to login.
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setMessage(R.string.login_dialog_message_article_body).setTitle(R.string.login_dialog_title_article_body);
                            builder.setPositiveButton(R.string.login_dialog_ok_button, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked OK button
                                    Intent loginIntent = new Intent(rootView.getContext(), LoginActivity.class);
                                    startActivity(loginIntent);
                                }
                            });
                            builder.setNeutralButton(R.string.login_dialog_purchase_button, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked Purchase button
                                    Intent subscribeIntent = new Intent(rootView.getContext(), SubscribeActivity.class);
                                    startActivity(subscribeIntent);
                                }
                            });
                            builder.setNegativeButton(R.string.login_dialog_cancel_button, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // User cancelled the dialog
                                }
                            });
                            AlertDialog dialog = builder.create();
                            dialog.show();

                        } else {
                            // Some other error.
                            Log.i("TOC", "Zip download failed with code: " + responseStatusCode + " and response: " + response.getStatusLine());
                            // Alert the user of the error.
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setMessage(getResources().getString(R.string.zip_download_dialog_message) + response.getStatusLine()).setTitle(R.string.zip_download_dialog_title);
                            builder.setPositiveButton(R.string.zip_download_dialog_ok_button, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked OK button
                                }
                            });
                            builder.setNeutralButton(R.string.zip_download_dialog_email_dev_button, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    // User wants to let us know about it...
                                    Intent intent = new Intent(Intent.ACTION_SEND);
                                    String[] email = new String[] {Helpers.getVariableFromConfig("EMAIL_ADDRESS")};
                                    intent.setType("text/plain");
                                    intent.putExtra(Intent.EXTRA_EMAIL, email);
                                    intent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.zip_download_dialog_email_subject));
                                    intent.putExtra(Intent.EXTRA_TEXT, getResources().getString(R.string.zip_download_dialog_email_body)
                                            + " '" + response.getStatusLine()
                                            + "' For IssueID: " + issue.getID());

                                    startActivity(Intent.createChooser(intent, getResources().getString(R.string.zip_download_dialog_email_chooser)));
                                }
                            });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                        }

                    } else {
                        // Error getting zip
                        Log.i("TOC", "Zip download failed! Response is null");
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setMessage(R.string.no_internet_dialog_message_article_body).setTitle(R.string.no_internet_dialog_title_article_body);
                        builder.setNegativeButton(R.string.no_internet_dialog_ok_button, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled the dialog
                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                }
            };
            Publisher.INSTANCE.setOnIssueZipDownloadCompleteListener(issueZipDownloadCompleteListener);

            return rootView;
        }

        private void populateLayoutListFromArticles(ArrayList<Article> articles) {

            // TODO: WATCH FOR MULTIPLES, AND EMPTY TITLES

            // Add category articles
            layoutList.add("Features");
            addArticlesToLayoutListWithCategoryName(articles, "features");

            // Add category articles
            layoutList.add("Agenda");
            addArticlesToLayoutListWithCategoryName(articles, "agenda");

            // Add category articles
            layoutList.add("Currents");
            addArticlesToLayoutListWithCategoryName(articles, "currents");

            // Add category articles
            layoutList.add("Film, Book & Music reviews");
            addArticlesToLayoutListWithCategoryNameWithExclusions(articles, "media",
                    new String[] {
                            "agenda",
                            "currents",
                            "viewfrom",
                            "mark-engler",
                            "steve-parry",
                            "kate-smurthwaite",
                            "finally",
                            "features"
                    });

            // Add category articles
            layoutList.add("Opinion");
            addArticlesToLayoutListWithCategoryName(articles, "argument");
            addArticlesToLayoutListWithCategoryName(articles, "viewfrom");
            addArticlesToLayoutListWithCategoryName(articles, "steve-parry");
            addArticlesToLayoutListWithCategoryName(articles, "mark-engler");
            addArticlesToLayoutListWithCategoryName(articles, "kate-smurthwaite");

            // Add category articles
            layoutList.add("Alternatives");
            addArticlesToLayoutListWithCategoryName(articles, "alternatives");

            // Add category articles
            layoutList.add("Regulars");
            addArticlesToLayoutListWithCategoryNameWithExclusions(articles, "columns",
                    new String[]{
                            "columns/currents",
                            "columns/media",
                            "columns/viewfrom",
                            "columns/mark-engler",
                            "columns/steve-parry",
                            "columns/kate-smurthwaite"
                    });
        }

        private void addArticlesToLayoutListWithCategoryNameWithExclusions(ArrayList<Article> articles, String categoryName, String[] exclusions) {
            for (Article article : articles) {
                Category : for (Category category : article.getCategories()) {
                    String thisCategoryName = category.getName();
                    for (String exclusion : exclusions) {
                        if (thisCategoryName.contains(exclusion)) {
                            continue Category;
                        }
                    }
                    if (thisCategoryName.contains(categoryName)) {
                        layoutList.add(article);
                    }
                }
            }
        }

        private void addArticlesToLayoutListWithCategoryName(ArrayList<Article> articles, String categoryName) {
            for (Article article : articles) {
                for (Category category : article.getCategories()) {
                    if (category.getName().contains(categoryName)) {
                        layoutList.add(article);
                    }
                }
            }
        }

        // Adapter for CardView
        public class TableOfContentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

            public Issue issue;
            private static final int TYPE_HEADER = 0;
            private static final int TYPE_CATEGORY = 1;
            private static final int TYPE_ARTICLE = 2;
            private static final int TYPE_FOOTER = 3;

            public TableOfContentsAdapter(Issue issue) {
                this.issue = issue;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View itemView = null;
                if (viewType == 0) {
                    // Header
                    itemView = LayoutInflater.
                            from(parent.getContext()).
                            inflate(R.layout.fragment_table_of_contents_header_view, parent, false);
                    return new TableOfContentsHeaderViewHolder(itemView);

                } else if (viewType == 1) {
                    // Category title
                    itemView = LayoutInflater.
                            from(parent.getContext()).
                            inflate(R.layout.fragment_table_of_contents_category_view, parent, false);
                    return new TableOfContentsCategoryViewHolder(itemView);

                } else if (viewType == 2) {
                    // Article
                    itemView = LayoutInflater.
                            from(parent.getContext()).
                            inflate(R.layout.fragment_table_of_contents_card_view, parent, false);
                    return new TableOfContentsViewHolder(itemView);

                } else if (viewType == 3) {
                    // Footer
                    itemView = LayoutInflater.
                            from(parent.getContext()).
                            inflate(R.layout.fragment_table_of_contents_footer_view, parent, false);
                    return new TableOfContentsFooterViewHolder(itemView);
                } else {
                    // Uh oh... didn't match view type.
                    return null;
                }
            }

            @Override
            public int getItemCount() {
                return layoutList.size() + 2; // 2 = header + footer
            }

            @Override
            public int getItemViewType(int position) {
                if (isPositionHeader(position)) {
                    return TYPE_HEADER;
                } else if (isPositionFooter(position)) {
                    return TYPE_FOOTER;
                } else if (isPositionCategory(position)) {
                    return TYPE_CATEGORY;
                }
                return TYPE_ARTICLE;
            }

            private boolean isPositionHeader(int position) {
                return position == 0;
            }

            private boolean isPositionFooter(int position) {
                return position == layoutList.size() + 1;
            }

            private boolean isPositionCategory(int position) {
                return layoutList.get(position - 1) instanceof String;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

                // Not recycling so that images don't appear in the wrong place
                holder.setIsRecyclable(false);

                if (holder instanceof TableOfContentsHeaderViewHolder) {
                    // Header
                    DateFormat dateFormat = new SimpleDateFormat("MMMM, yyyy", Locale.getDefault());
                    String issueNumberDate = Integer.toString(issue.getNumber()) + " - " + dateFormat.format(issue.getRelease());
                    ((TableOfContentsHeaderViewHolder) holder).issueNumberDateTextView.setText(issueNumberDate);

                    final ImageView coverImageView = ((TableOfContentsHeaderViewHolder) holder).issueCoverImageView;
                    issue.getCoverCacheStreamFactoryForSize((int) getResources().getDimension(R.dimen.toc_cover_width)).preload(new CacheStreamFactory.CachePreloadCallback() {
                        @Override
                        public void onLoad(byte[] payload) {
                            if (payload != null && payload.length > 0) {
                                Bitmap coverBitmap = BitmapFactory.decodeByteArray(payload,0,payload.length);
                                coverImageView.setImageBitmap(coverBitmap);

                            }
                        }

                        @Override
                        public void onLoadBackground(byte[] payload) {

                        }
                    });

                } else if (holder instanceof TableOfContentsCategoryViewHolder) {
                    // Category title
                    String categoryTitle = (String) layoutList.get(position - 1); // Header
                    ((TableOfContentsCategoryViewHolder) holder).categoryNameTextView.setText(categoryTitle);

                } else if (holder instanceof TableOfContentsViewHolder) {
                    // Article
                    Article article = (Article) layoutList.get(position - 1); // Header
                    ArrayList<Image> images = article.getImages();
                    ((TableOfContentsViewHolder) holder).articleTitleTextView.setText(article.getTitle());
                    String articleTeaser = article.getTeaser();
                    TableOfContentsViewHolder tableOfContentsViewHolder = ((TableOfContentsViewHolder) holder);
                    if (articleTeaser != null && !articleTeaser.isEmpty()) {
                        tableOfContentsViewHolder.articleTeaserTextView.setVisibility(View.VISIBLE);
                        tableOfContentsViewHolder.articleTeaserTextView.setText(Html.fromHtml(articleTeaser));
                    } else {
                        // Remove teaser view.
                        tableOfContentsViewHolder.articleTeaserTextView.setVisibility(View.GONE);
                    }

                    final ImageView articleImageView = ((TableOfContentsViewHolder) holder).articleImageView;
                    if (images.size() > 0) {
                        images.get(0).getImageCacheStreamFactoryForSize(MainActivity.applicationContext.getResources().getDisplayMetrics().widthPixels).preload(new CacheStreamFactory.CachePreloadCallback() {
                            @Override
                            public void onLoad(byte[] payload) {
                                if (payload != null && payload.length > 0) {
                                    Bitmap coverBitmap = BitmapFactory.decodeByteArray(payload,0,payload.length);
                                    articleImageView.setImageBitmap(coverBitmap);

                                }
                            }

                            @Override
                            public void onLoadBackground(byte[] payload) {

                            }
                        });
                    } else {
                        articleImageView.setVisibility(View.GONE);
                    }

                } else if (holder instanceof TableOfContentsFooterViewHolder) {
                    // Footer
                    // Get editor image.
                    final ImageView editorImageView = ((TableOfContentsFooterViewHolder) holder).editorImageView;
                    if (editorImageView != null) {

                        issue.getEditorsImageCacheStreamFactoryForSize((int) getResources().getDimension(R.dimen.toc_editors_image_width), (int) getResources().getDimension(R.dimen.toc_editors_image_height)).preload(new CacheStreamFactory.CachePreloadCallback() {
                            @Override
                            public void onLoad(byte[] payload) {
                                if (payload != null && payload.length > 0) {
                                    Bitmap editorsImageBitmap = BitmapFactory.decodeByteArray(payload, 0, payload.length);
                                    editorImageView.setImageDrawable(Helpers.roundDrawableFromBitmap(editorsImageBitmap));
//                                    TableOfContentsAdapter.this.notifyItemChanged(TableOfContentsAdapter.this.getItemCount()-1);
                                }
                            }

                            @Override
                            public void onLoadBackground(byte[] payload) {

                            }
                        });
                    }

                    ((TableOfContentsFooterViewHolder) holder).editorsLetterTextView.setText(Html.fromHtml(issue.getEditorsLetterHtml()));
                    ((TableOfContentsFooterViewHolder) holder).editorsNameTextView.setText("Edited by:\n" + issue.getEditorsName());
                }
            }


            public class TableOfContentsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

                public TextView articleTitleTextView;
                public TextView articleTeaserTextView;
                public ImageView articleImageView;

                public TableOfContentsViewHolder(View itemView) {
                    super(itemView);
                    articleTitleTextView = (TextView) itemView.findViewById(R.id.toc_article_title);
                    articleTeaserTextView = (TextView) itemView.findViewById(R.id.toc_article_teaser);
                    articleImageView = (ImageView) itemView.findViewById(R.id.toc_article_image);
                    itemView.setOnClickListener(this);
                }

                @Override
                public void onClick(View v) {
//                    Toast.makeText(MainActivity.applicationContext, "View clicked at position: " + getPosition(), Toast.LENGTH_SHORT).show();
                    Intent articleIntent = new Intent(MainActivity.applicationContext, ArticleActivity.class);
                    // Pass issue through as a Parcel
                    articleIntent.putExtra("article", (Article) layoutList.get(getPosition() - 1));
                    articleIntent.putExtra("issue", issue);
                    startActivity(articleIntent);
                }
            }

            public class TableOfContentsCategoryViewHolder extends RecyclerView.ViewHolder {

                public TextView categoryNameTextView;

                public TableOfContentsCategoryViewHolder(View itemView) {
                    super(itemView);
                    categoryNameTextView = (TextView) itemView.findViewById(R.id.toc_category_name);
                }
            }

            public class TableOfContentsHeaderViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

                public ImageView issueCoverImageView;
                public TextView issueNumberDateTextView;
                public ProgressBar zipDownloadProgressSpinner;

                public TableOfContentsHeaderViewHolder(View itemView) {
                    super(itemView);
                    issueCoverImageView = (ImageView) itemView.findViewById(R.id.toc_cover);
                    issueNumberDateTextView = (TextView) itemView.findViewById(R.id.toc_issue_number_date);
                    zipDownloadProgressSpinner = (ProgressBar) itemView.findViewById(R.id.toc_zip_loading_spinner);
                    issueCoverImageView.setOnClickListener(this);
                    issueCoverImageView.setOnLongClickListener(this);
                }

                @Override
                public void onClick(View v) {
                    Intent imageIntent = new Intent(MainActivity.applicationContext, ImageActivity.class);
                    // Pass image url through as a Parcel
                    imageIntent.putExtra("url", issue.getCoverLocationOnFilesystem().getAbsolutePath());
                    imageIntent.putExtra("issue", issue);
                    startActivity(imageIntent);
                }

                @Override
                public boolean onLongClick(View v) {
                    Log.i("TOC", "Cover long click detected!");
                    // Ask the user if they'd like to delete this issue's cache
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage(R.string.toc_dialog_delete_cache_message).setTitle(R.string.toc_dialog_delete_cache_title);
                    builder.setNeutralButton(R.string.toc_dialog_delete_cache_ok_button, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User clicked OK button
                            // Delete this issue cache
                            issue.deleteCache();
                            getActivity().finish();
                        }
                    });
                    builder.setPositiveButton(R.string.toc_dialog_download_zip_button, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // Download the zip from Rails and unpack it...
                            issue.downloadZip(purchases);
                            zipDownloadProgressSpinner.setVisibility(View.VISIBLE);
                        }
                    });
                    builder.setNegativeButton(R.string.toc_dialog_delete_cache_cancel_button, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog, so do nothing.
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();

                    // Return true to consume the click
                    return true;
                }
            }

            public class TableOfContentsFooterViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

                public ImageView editorImageView;
                public TextView editorsLetterTextView;
                public TextView editorsNameTextView;

                public TableOfContentsFooterViewHolder(View itemView) {
                    super(itemView);
                    editorImageView = (ImageView) itemView.findViewById(R.id.toc_editor_image);
                    editorsLetterTextView = (TextView) itemView.findViewById(R.id.toc_editors_letter);
                    editorsNameTextView = (TextView) itemView.findViewById(R.id.toc_editors_name);
                    editorImageView.setOnClickListener(this);
                }

                @Override
                public void onClick(View v) {
                    Intent imageIntent = new Intent(MainActivity.applicationContext, ImageActivity.class);
                    // Pass image url through as a Parcel
                    imageIntent.putExtra("url", issue.getEditorsLetterLocationOnFilesystem().getAbsolutePath());
                    imageIntent.putExtra("issue", issue);
                    startActivity(imageIntent);
                }
            }
        }
    }
}
