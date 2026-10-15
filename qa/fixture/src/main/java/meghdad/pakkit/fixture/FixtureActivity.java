package meghdad.pakkit.fixture;
public final class FixtureActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("Installation verified\nPakkit QA fixture");
        text.setTextColor(0xffd9f99d); text.setBackgroundColor(0xff0e211b);
        text.setGravity(android.view.Gravity.CENTER); text.setTextSize(18);
        text.setPadding(36,36,36,36); setContentView(text);
    }
}
