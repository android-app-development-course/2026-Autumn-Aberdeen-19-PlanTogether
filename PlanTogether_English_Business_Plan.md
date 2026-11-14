PLANTOGETHER

# Group Travel Planning App Business Plan

A shared workspace for trip ideas, places, decisions and plans

Product concept  ·  MVP scope  ·  Business model  ·  Validation roadmap

Version 1.0  |  October 2026

## Executive Summary

PlanTogether is a lightweight planning and collaboration app for people traveling together. It gives a trip organizer one shared place to collect links and user-entered details, arrange activities by day, invite companions to comment or vote, and view planned locations on a map. The initial product is designed to work without authorized third-party APIs or platform integrations.

> **Investment thesis to test**<br>The first question is whether an organizer can save time and reach group decisions by turning scattered links and chat messages into one shared trip plan. Validate that behavior before investing in broad distribution or paid integrations.

### App Overview

Group trips often begin with restaurant recommendations, short videos, map pins and ticket pages. Useful information becomes fragmented across apps and group chats. Dates, budgets, meeting times and backup choices then require repeated clarification. PlanTogether organizes the information that users choose to submit and helps a group turn it into an actionable itinerary.

The app is a planning and collaboration layer, not a replacement for maps, review sites, ticketing services or travel marketplaces. Users continue discovering content and purchasing services where they already do so; PlanTogether supports the coordination around those choices.

### Version Scope and Data Boundaries

| Included in this version | Explicitly excluded |
| --- | --- |
| User-entered names, addresses, coordinates, times, budgets, notes and source descriptions. | Automatic scraping or crawling of maps, Dianping, Ctrip, TikTok, Bilibili or other services. |
| Links pasted or shared by the user, stored as links with user-written titles and notes. | API-based searches, automatic import of restricted content or creator/video catalogs. |
| Map markers from manually entered locations or user-selected map links; manual ordering and route sketches. | Live ticket prices or availability, price alerts, navigation estimates, booking or payment integrations. |

Any future use of a map or content API would require separate review of permissions, terms, privacy, reliability and cost. It is not a current feature commitment.

## Product Modules and User Flow

### Core Modules

| Module | What it does | User benefit |
| --- | --- | --- |
| Trip workspace | Creates a trip with destination, date range, group size, visibility and an invitation link. | Keeps trip information together. |
| Link inbox | Accepts a pasted URL or a user submission through the share sheet; the submitter adds a title, source, tags and notes. | Preserves inspiration and its source without copying external pages. |
| Itinerary and places | Lets users add a place, date, time, budget estimate, stay length, notes and backup option. | Turns saved ideas into a usable schedule. |
| Map view | Displays markers from user-entered addresses or coordinates; supports manual ordering and illustrative connecting lines. | Makes the group’s planned sequence easier to discuss. |
| Group decisions | Supports comments, votes, candidate options, owners and decision status, with edit history. | Gives companions a clear way to participate. |
| Budget and checklist | Tracks manually entered cost estimates and pre-trip tasks. | Aligns expectations without live price feeds. |

### Typical User Flow

| Step | User action | Product response |
| --- | --- | --- |
| 1. Create | Organizer enters trip basics and invites companions. | A shared trip workspace is created. |
| 2. Collect | Members share links from maps, review pages, ticket pages or videos and add their own notes. | Items are grouped by source, day or tag; page content is not automatically read. |
| 3. Organize | Organizer creates places and manually enters schedule, coordinates, costs and alternatives. | An itinerary and user-supplied map markers take shape. |
| 4. Decide | Members comment, vote and confirm preferred options. | The group can see choices and decision status. |
| 5. Travel | Members use the final itinerary, map markers, route sketch and outbound map links. | Navigation and purchases happen in the selected external service. |

### Map and Route Planning in This Version

The map is a visual planning aid. A place may be created from a name, address or coordinate entered by a user, or from a map link the user chooses to share and then completes manually. Markers follow the dates and order selected by the group. Users may reorder stops and draw a simple route sketch. PlanTogether does not calculate road distance, travel time, transit options, live traffic or opening status. A user-provided map link opens in the destination map app, which supplies whatever functionality it supports.

## Unique Selling Proposition and Target User

### Unique Selling Proposition

> **Core promise**<br>Turn the trip ideas your group has already found into a plan everyone can review, shape and use together.

The advantage is the workflow, not ownership of third-party content. Each idea keeps its source link; users add the fields they need. Places, time slots, alternatives, votes and the planned order live in the shared trip. Members keep their familiar discovery and booking habits while moving group decisions into one workspace.

### Differentiation to Validate

- Designed around group coordination: membership, candidate options, votes and decisions are core objects rather than add-ons to a personal bookmark list.
- Source-transparent and user-controlled: the app records the submitter, link and user-written notes instead of building an unauthorized copy of external content.
- Planning connects to execution: map markers, itinerary order, budget and pre-trip checklist share one trip record.
- Low-friction adoption: companions can join a plan without changing where they discover content or buy tickets.
Alternatives include group chats, shared documents and spreadsheets, map lists, travel-planning apps and travel agencies. This plan does not assume those alternatives lack features; user interviews and hands-on competitor reviews must establish the actual gap.

### Target Persona

| Persona | Needs and behavior | Trigger situation |
| --- | --- | --- |
| Primary: trip organizer | Typically 22–38; joins several friend or partner trips a year; collects recommendations and ends up coordinating dates, schedules and follow-ups. Values a fast, low-effort tool. | A weekend getaway or short city trip with roughly 3–8 companions. |
| Collaborator: travel companion | May not want another app; wants to review a plan, weigh in on food or activities and add a link. | Receives an invitation and needs to confirm preferences quickly. |
| Later segment: clubs and small teams | Repeats group outings and may need reusable templates, member management and trip history. | A club trip, hobby group excursion or informal team event. |

### Market Entry

Start with friend-group short trips, where coordination pain is visible and a decision-maker can be reached directly. Select one city, campus or interest community based on interview access and pilot recruitment. Build a bottom-up opportunity model from reachable groups, trip frequency, invitation acceptance and repeat use rather than relying on a broad tourism-market estimate.

## Marketing and Acquisition Channels

Early marketing should demonstrate a concrete planning job: collect a group’s ideas, agree on a shortlist and leave with a shared plan. Avoid claims that the product automatically gathers or compares external platform data.

| Channel | Execution | Early measures |
| --- | --- | --- |
| Travel creators | Partner with creators covering trip planning, city walks, family travel or group outings. Demonstrate a public template and disclose paid relationships. | Link visits, trip creation, invited members and cost per activated trip. |
| Short-form video and communities | Publish planning tips, reusable itineraries and examples of group decision workflows on suitable channels such as TikTok and Bilibili, subject to local availability and platform rules. | Reach, saves, shares, landing-page conversion and completed first plans. |
| Campus and interest groups | Recruit club leads, outdoor groups and photography communities for a small pilot using free access and reusable templates. | Groups recruited, member activation, 30/60-day reuse and feedback completion. |
| Product referrals | Make inviting companions, copying a template and sharing a final itinerary natural product actions; test non-cash benefits. | Invites per organizer, invitation acceptance and new trips from shared plans. |
| Search and content | Publish city-weekend templates, group budget worksheets and practical guides to organizing decisions. | Organic visits, template copies, registration and cost per qualified user. |

### Channel Testing Rules

- Test one message and one audience at a time so channel results remain interpretable.
- Define an activated trip as a created plan with at least one companion invited and at least one place or link entered.
- Compare channels by activated trips and repeat use, not just views or installs.
- Use platform marks and creator materials only in ways permitted by the relevant service and creator agreement.
### Launch Sequence

Begin with founder-led recruitment and a small number of community partners. Use the resulting interviews and observed usage to improve onboarding and templates. Add paid promotion only after the team can identify which groups activate, which invitations are accepted and whether organizers return for another trip.

## Business Model and Illustrative Financials

### Revenue Hypotheses

- Stage 1: a free core product to reduce the cost of inviting an entire group and to validate collaborative use.
- Stage 2: test paid individual or trip-based features such as more workspaces, templates, exports, permissions and trip-history reuse. Set prices through willingness-to-pay experiments.
- Stage 3: consider clearly labeled destination partnerships or sponsorships only if they do not distort user votes or require unauthorized data access. Do not sell personal trip data.
### Illustrative Scenario (RMB)

Every value below is an illustrative planning assumption, not actual performance, a market quote, a funding commitment or a verified forecast. The scenario assumes subscription revenue only; it includes no transaction commissions and no monetization of third-party platform data.

| Metric | Year 1 | Year 2 | Year 3 |
| --- | --- | --- | --- |
| Registered users at year-end | 8,000 | 30,000 | 80,000 |
| Average monthly active users | 1,500 | 6,500 | 18,000 |
| Assumed paid conversion | 2% | 3% | 4% |
| Assumed monthly revenue per payer | ¥18 | ¥20 | ¥22 |
| Estimated annual revenue | ¥0.65w | ¥4.68w | ¥19.01w |
| Assumed annual operating costs | ¥120w | ¥220w | ¥360w |
| Estimated operating result | -¥119.35w | -¥215.32w | -¥340.99w |

Formula: annual revenue = average monthly active users × paid conversion × monthly revenue per payer × 12. For simplicity, the calculation assumes a stable average user base and uniform payment through each year. Actual revenue would depend on launch timing, retention, discounts, refunds, taxes and channel fees. Operating costs are planning assumptions for staffing, product development, cloud services, marketing, support, compliance and administration. The scenario remains loss-making in all three years and must be rebuilt with observed data; it is not evidence of commercial viability.

### Key Operating Metrics

| Metric | Definition and use |
| --- | --- |
| Activated trips | Trips that add at least one companion and one place or link within seven days of creation. |
| Invitation acceptance | Members who join divided by invitations sent; a measure of group-start friction. |
| Collaborative completion | Share of trips where at least two people contribute a comment, vote or item. |
| Organizer reuse | Organizers who create another trip within 90 days. |
| Paid conversion and retention | Track conversion, renewal, refunds and cancellation reasons by cohort. |
| Acquisition cost and contribution margin | Compare channel cost with subscription revenue less variable costs such as payment and cloud fees. |

## Validation Plan

Validation should focus on what people did during their last group trip, where coordination broke down, why current tools were insufficient and whether they will invest real time in a trial. Do not treat polite reactions to a pitch as proof of demand.

| Stage | Method and target | Signal and decision gate |
| --- | --- | --- |
| Problem interviews (Weeks 1–2) | Interview 20–30 people who recently organized a group trip. Ask about their actual workflow, tools, rework and coordination failures. | At least half describe a specific, recurring organization or decision problem. If not, revisit the user segment or problem. |
| Concept and prototype (Weeks 2–4) | Test a clickable prototype or manually assisted workflow for adding links and places, voting and viewing the map with 10–15 groups. | At least 60% of groups complete a core shared-plan task within 10 minutes; identify the largest point of friction. |
| Closed MVP (Weeks 5–10) | Recruit 50–100 trip groups and observe creation, invitation, collaboration, completion and post-trip feedback. | At least 40% invite a companion; at least 30% show contributions from two or more people; look for 30/60-day reuse. |
| Pricing experiment (Weeks 8–12) | Show active users alternative paid feature bundles and transparent prices. Use an interest page or non-binding intent registration if payment is premature. | Record meaningful clicks, waitlist or purchase intent and interview reasons. If evidence is weak, continue free-product learning. |

### Experiment Discipline

These sample sizes and thresholds are initial team-designed experiments, not industry benchmarks. For each test, document the assumption, method, observed behavior, limitations and resulting decision. Prioritize past behavior and actual participation over hypothetical questions such as “Would you use this?”

### Questions to Answer Before Scaling

- Can organizers create a useful shared trip quickly without help from the team?
- Will companions accept an invitation and contribute, rather than only view the itinerary?
- Does the product reduce repeated clarification or planning effort in a way users notice?
- Do organizers return for another trip, and which premium capability would justify payment?
The approach is informed by rapid experimentation practices described by Strategyzer and customer-problem discovery guidance in the Y Combinator Startup Library. See the references at the end of this plan.

## MVP Scope and Development Roadmap

### MVP Must Include

- Lightweight sign-in, a trip workspace, invitation links and basic member permissions.
- Manual entry of places, times, budgets and notes; link sharing with user-written title, summary, source and tags.
- A day-by-day itinerary and map markers based on user-provided addresses or coordinates, with manual ordering and route sketches.
- Comments, candidate-option votes, decision status, edit history and a pre-trip checklist.
- Clear sharing controls, data deletion and export or clearing mechanisms, with privacy-first collection.
### Out of Scope

Automatic page parsing or scraping; platform API searches or synchronization; automatic import of restricted content; creator or video catalogs; live ticket prices or inventory; booking recommendations, checkout, payments or reservations; automatic navigation, live traffic or travel-time calculations; and unvalidated AI-generated itineraries.

### 12-Month Roadmap

| Timing | Work | Deliverable and decision |
| --- | --- | --- |
| Months 0–1 | Interviews, competitor walkthroughs, prototype and manual service experiment; define link-sharing and privacy behavior. | Choose the most important group scenario, core tasks and recruitment channel. |
| Months 2–3 | Build closed MVP: trip workspace, manual links and places, votes, map markers and stop ordering. | Pilot with 50–100 groups; decide whether activation and collaboration justify continued build. |
| Months 4–6 | Improve invitations and mobile usability; add templates, permissions, notifications and export; run small creator/community tests. | Assess repeat use, invitation-led growth and support cost. |
| Months 7–9 | Prototype premium features and pricing; improve analytics, support and security operations. | Test paid plans only after there is behavioral evidence of user value. |
| Months 10–12 | Review unit economics, retention and channel performance; decide whether to expand or narrow the segment. | Set the next-year investment and product plan. Any API integration requires separate authorization and cost review. |

### Release Principle

Keep the first release small enough to learn from real trips. Prioritize the steps between collecting a link and getting a group decision. Add complexity only when observed behavior shows that the current manual workflow is a meaningful bottleneck.

## Risks, Conclusion and References

| Risk | Potential impact | Mitigation |
| --- | --- | --- |
| External links change or fail | A page may become unavailable or require a login. | Store the URL and user-written context; explain that source platforms control link access; allow editing and failure reporting. |
| Platform terms and intellectual property | Improper copying, scraping or branding could cause complaints or removal. | Use user-submitted links and minimal storage; do not bypass access controls; review terms and establish a complaint process. |
| Map capability expectations | Users may assume automatic routing or live navigation. | Label the map as manual planning; do not promise distance or time; hand off through user-provided map links. |
| Group cold start | A solo organizer may not experience the core value if companions do not join. | Reduce join friction; offer templates; measure invitation acceptance and collaborative completion. |
| Infrequent use and seasonality | Travel is not a daily habit, so monthly activity may understate value. | Measure trip lifecycle completion and organizer reuse, not only monthly activity. |
| Weak willingness to pay | Free substitutes may be sufficient to users. | Test premium features in small cohorts; delay fixed-cost expansion and define cost gates. |
| Personal information and location safety | A leaked itinerary or location may expose users to real-world risk. | Collect the minimum needed; support revocable sharing and clear permissions; implement deletion, encryption, logging and response procedures. |

### Conclusion

PlanTogether’s viability depends on one practical question: can a shared trip plan help an organizer collect information and reach group decisions with less repeated coordination? The team can test this value using user-submitted links, manually entered places and collaboration tools without owning external platform data.

Start with friend-group short trips. Confirm the problem through interviews, then release a tightly scoped closed MVP. Expand marketing and test paid features only if groups invite companions, collaborate and return for another trip. Update the illustrative financial scenario with observed data. Evaluate any authorized API or platform integration separately before considering it.

### References

1. U.S. Small Business Administration, “Write Your Business Plan.” Business-plan structure, market analysis, marketing and financial projections. https://www.sba.gov/business-guide/plan-your-business/write-your-business-plan

2. U.S. Small Business Administration, “Plan Your Business.” Market research, competitive analysis and break-even framing. https://www.sba.gov/counseling/plan-your-business/

3. Strategyzer, “Testing Business Ideas.” Rapid experiments and evidence for business assumptions. https://www.strategyzer.com/library/testing-business-ideas-book

4. Y Combinator Startup Library, “How To Pick A Startup Idea.” User-problem discovery and early validation. https://www.ycombinator.com/library/Ri-how-to-pick-a-startup-idea

Accessed October 19, 2026. These sources inform business-plan structure and validation methods; product recommendations and financial figures are planning assumptions.
