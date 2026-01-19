#!/usr/bin/env python3
"""
Generate test data for Machine Events API
Creates JSON file with 1000 events for performance testing
"""

import json
import random
from datetime import datetime, timedelta

def generate_events(count=1000):
    """Generate test events with realistic data"""
    events = []
    base_time = datetime(2026, 1, 15, 10, 0, 0)

    # Configuration
    machines = ["M-001", "M-002", "M-003", "M-004", "M-005"]
    lines = ["LINE-1", "LINE-2", "LINE-3"]
    factories = ["F01"]

    print(f"🔧 Generating {count} test events...")

    for i in range(count):
        # Create event with incremental time (10 seconds apart)
        event_time = base_time + timedelta(seconds=i * 10)

        event = {
            "eventId": f"E-PERF-{i+1}",
            "eventTime": event_time.strftime("%Y-%m-%dT%H:%M:%S.000Z"),
            "machineId": random.choice(machines),
            "durationMs": random.randint(500, 5000),
            "defectCount": random.randint(0, 10),
            "lineId": random.choice(lines),
            "factoryId": random.choice(factories)
        }
        events.append(event)

        # Progress indicator
        if (i + 1) % 100 == 0:
            print(f"  ✓ Generated {i + 1}/{count} events")

    return events

def save_to_file(events, filename="test-1000-events.json"):
    """Save events to JSON file"""
    with open(filename, "w") as f:
        json.dump(events, f, indent=2)
    print(f"\n✅ Successfully saved {len(events)} events to: {filename}")

    # Calculate file size
    import os
    file_size = os.path.getsize(filename)
    print(f"📦 File size: {file_size / 1024:.2f} KB")

def main():
    print("=" * 60)
    print("  Machine Events - Test Data Generator")
    print("=" * 60)
    print()

    # Generate events
    events = generate_events(1000)

    # Save to file
    save_to_file(events)

    # Print usage instructions
    print()
    print("=" * 60)
    print("📋 How to use this file:")
    print("=" * 60)
    print()
    print("1. Make sure your Spring Boot app is running:")
    print("   mvn spring-boot:run")
    print()
    print("2. Run benchmark test:")
    print("   time curl -X POST http://localhost:8080/events/batch \\")
    print("     -H 'Content-Type: application/json' \\")
    print("     -d @test-1000-events.json")
    print()
    print("3. Or use Postman:")
    print("   - Method: POST")
    print("   - URL: http://localhost:8080/events/batch")
    print("   - Body: Upload test-1000-events.json")
    print()
    print("=" * 60)
    print("✨ Test data generation complete!")
    print("=" * 60)

if __name__ == "__main__":
    main()