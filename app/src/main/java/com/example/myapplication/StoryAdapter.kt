import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.Story

class StoryAdapter(
    private var stories: List<Story>
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    fun submitList(newStories: List<Story>) {
        stories = newStories
        notifyDataSetChanged()
    }

    class StoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.titleTextView)
        val url: TextView = itemView.findViewById(R.id.urlTextView)
        val score: TextView = itemView.findViewById(R.id.scoreTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_layout, parent, false)
        return StoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        val story = stories[position]

        holder.title.text = story.title
        holder.url.text = story.url
        holder.score.text = "Score: ${story.score}"

        holder.itemView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(story.url))
            it.context.startActivity(intent)
        }
    }


    override fun getItemCount() = stories.size
}
