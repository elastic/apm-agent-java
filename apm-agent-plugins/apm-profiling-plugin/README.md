# Profiling plugin

## Known issues

### Missing inferred spans

- After each profiling session, while the stack traces and activation events are processed, no traces are collected.
  - Under load, processing can take seconds; ~200ms are normal.
  - Log:
    ```
    DEBUG Processing {} stack traces
    ...
    DEBUG Processing traces took {}µs
    ```
- While stack traces are processed, activation events are still put into the ring buffer. However, they don't get processed. If, during this period, there are more activation events than the buffer can handle, we're losing activation events.
  - Log: `Could not add activation event to ring buffer as no slots are available`
  - Lost activation events can lead to orpaned call trees (lost end event), missing roots (lost start event) and messed up parent/child relationships (lost span activations/deactivations)
  Log:
  ```
  DEBUG Illegal state ...
  ```
- Under load, the activation event ring buffer can also get full
- The actual `profiling_sampling_interval` might be a bit lower. async-profiler aims to keep the interval relatively consistent but if there are too many threads actively running transactions or if there's a traffic spike, the interval can be lower.
- As a result of the above, some transactions don't contain inferred spans, even if their duration is longer than `profiling_sampling_interval`.
  Log:
  ```
  DEBUG Created no spans for thread {} (count={})
  ```
- If the sampling rate is high and there's lots of traffic, the amount of inferred spans may flood the internal queue, leading to lost events (transactions, regular spans, or inferred spans).
  Log:
  ```
  DEBUG Could not add {} {} to ring buffer as no slots are available
  ```
- The UI currently doesn't favor trace samples with inferred spans
- To find out about how many transactions with inferred spans there are
  ```
  POST /apm*/_search
  {
    "size": 0,
    "query": {
      "term": {
        "span.subtype": {
          "value": "inferred"
        }
      }
    },
    "aggs": {
      "traces_with_inferred_spans": {
        "cardinality": {
          "field": "trace.id"
        }
      }
    }
  }
  ```
- There can be a race condition when putting activation events into the queue which leads to older events being in front of newer ones, like `1, 2, 4, 3, 5`. But this is quite infrequent and the consequences are similar to loosing that activation event or event without any consequence.
  Log:
  ```
  Timestamp of current activation event ({}) is lower than the one from the previous event ({})
  ```
### Incorrect parent/child relationships

#### Without workaround

Inferred span starts after actual span, even though it should be the parent
```
 ---------[inferred ]
 [actual]
^         ^         ^
```


Inferred span ends before actual span, even though it should be the parent
```
[inferred   ]------------
             [actual]
^           ^           ^
```

```
   -------[inferred ]-------                 [actual         ]      
       [actual         ]          ->     -------[inferred ]-------
^         ^         ^         ^
```

Two consecutive method invocations are interpreted as one longer execution
```
[actual]   [actual]   ->  [--------  --------]
^          ^              
```
#### With non-implemented workaround

Actual spans can't be a child of an inferred span, as inferred spans are sent later (after profiling session ends)
```
[transaction   ]     [transaction   ]  
└─[inferred  ]    -> ├─[inferred  ]    
  └─[actual]         └───[actual]      
```

#### With workaround

Parent inferred span ends before child. Workaround: set end timestamp of inferred span to end timestamp of actual span.
```
[inferred ]--------         [inferred  -----]--
         [actual]       ->           [actual]
^         ^         ^     
```

Parent inferred span starts after child. Workaround: set start timestamp of inferred span to start timestamp of actual span.
```
  --------[inferred ]          --[------inferred ]
    [actual ]           ->       [actual ]        
^         ^         ^
```

#### Example

In this screenshot, we can see several problems at once
<img width="1137" alt="inferred spans issues" src="https://user-images.githubusercontent.com/2163464/75677751-710bd880-5c8c-11ea-8bd9-1c6d5f3268d5.png">
