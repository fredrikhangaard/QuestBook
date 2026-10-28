package no.ntnu.prog2007.questbook.ui.groups

import androidx.lifecycle.ViewModel

/** Group summary shown on Home and Groups. Replace sample data with shared group storage later. */
data class GroupSummary(val id: String, val name: String, val members: List<String>, val description: String)

/** Shared sample data keeps Home, the group overview, and detail routes consistent. */
class GroupsViewModel : ViewModel() {
    val groups = listOf(
        GroupSummary("mountain-crew", "Mountain Crew", listOf("Emma", "Jonas", "Sara"),
            "Outdoor adventures and quests together.")
    )
}
